package org.rootive.nio;

import org.rootive.util.Linked;
import org.rootive.log.LogLine;
import org.rootive.log.Logger;

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class RUDPConnection {
    static public class Datagram {
        public int c;
        public ByteBuffer b;

        public Datagram(int c, ByteBuffer b) {
            this.c = c;
            this.b = b;
        }
    }

    public enum State {
        Connecting, Connected, Disconnecting, Disconnected
    }
    public enum Operator {
        Connect, ConnectConfirm,
        Message, MessageConfirm,
        Disconnect, DisconnectConfirm,
        Heartbeat
    }

    public static final int MTU = 548;
    private static final int checkSize = 1;
    private static final int operatorSize = 1;
    private static final int countSize = 4;
    public static final int headerSize = checkSize + operatorSize + countSize;

    static private final int connectPeriod = 1000;
    static private final int connectMissPeriod = 8000;
    static private final int heartbeatPeriod = 16000;
    static private final int heartbeatMissPeriod = 32000;
    static private final int pardonPeriod = 2000;

    private final SocketAddress remote;
    private final Loop loop;

    private final BiConsumer<SocketAddress, ByteBuffer> transmission;
    private BiConsumer<RUDPConnection, Linked<ByteBuffer>> readCallback;
    private Consumer<RUDPConnection> connectCallback;
    private Consumer<RUDPConnection> disconnectCallback;
    private Consumer<RUDPConnection> flushedCallback;

    private final ScheduledThreadPoolExecutor timers;
    private ScheduledFuture<?> heartbeatFuture;
    private ScheduledFuture<?> missFuture;
    private ScheduledFuture<?> pardonFuture;

    private State state = State.Connecting;
    private boolean bFlushing;
    private byte check;
    private int sentCount;

    private Linked<Datagram> unsent = new Linked<>();
    private Linked<ByteBuffer> received = new Linked<>();
    private final Linked<Datagram> unconfirmed = new Linked<>();
    private int receivedCount;

    public Object context;

    public RUDPConnection(SocketAddress remote, Loop loop, BiConsumer<SocketAddress, ByteBuffer> transmission, ScheduledThreadPoolExecutor timers) {
        this.remote = remote;
        this.loop = loop;
        this.transmission = transmission;
        this.timers = timers;
    }

    public SocketAddress getRemote() {
        return remote;
    }
    public void setReadCallback(BiConsumer<RUDPConnection, Linked<ByteBuffer>> readCallback) {
        this.readCallback = readCallback;
    }
    public void setConnectCallback(Consumer<RUDPConnection> connectCallback) {
        this.connectCallback = connectCallback;
    }
    public void setDisconnectCallback(Consumer<RUDPConnection> disconnectCallback) {
        this.disconnectCallback = disconnectCallback;
    }
    public void setFlushedCallback(Consumer<RUDPConnection> flushedCallback) {
        this.flushedCallback = flushedCallback;
    }

    void handleReceive(ByteBuffer b) {
        loop.run(() -> {
            if (state == State.Disconnected) {
                return;
            }
            if (b.remaining() >= checkSize + operatorSize) {
                setMiss(heartbeatMissPeriod);

                var c = b.get();
                if (c > check) {
                    clear();
                    check = c;
                }
                if (c == check) {
                    var o = Operator.values()[b.get()];
                    switch (o) {
                        case Connect -> handleConnect();
                        case ConnectConfirm -> handleConnectConfirm();
                        case Message -> {
                            if (b.remaining() >= countSize) {
                                var count = b.getInt();
                                handleMessage(count, b);
                            }
                        }
                        case MessageConfirm -> {
                            if (b.remaining() >= countSize) {
                                var count = b.getInt();
                                handleMessageConfirm(count);
                            }
                        }
                        case Disconnect -> handleDisconnect();
                        case DisconnectConfirm -> handleDisconnectConfirm();
                    }
                }
            }
        });
    }

    private void doUnsent() {
        if (bFlushing || state != State.Connected) {
            return;
        }
        var n = unsent.head().find(Objects::isNull);
        Linked<Datagram> ready;
        if (n != null) {
            ready = unsent.lSplit(n);
        } else {
            ready = unsent;
            unsent = new Linked<>();
        }

        if (!ready.isEmpty()) {
            var rn = ready.head();
            do {
                transmission.accept(remote, rn.v.b.duplicate());
                rn = rn.right();
            } while (rn != null);
            unconfirmed.link(ready);
            setHeartbeat();
        }

        if (!unsent.isEmpty() && unsent.head().v == null && !unconfirmed.isEmpty()) {
            unsent.removeFirst();
            bFlushing = true;
            setPardon();
        }
    }
    private void setHeartbeat() {
        if (heartbeatFuture != null) {
            heartbeatFuture.cancel(false);
        }
        heartbeatFuture = timers.scheduleAtFixedRate(() -> {

            ByteBuffer b = ByteBuffer.allocate(checkSize + operatorSize);
            b.put(check);
            b.put((byte) Operator.Heartbeat.ordinal());
            b.flip();
            LogLine.begin(Logger.Level.Debug).log("send heartbeat to " + remote).end();
            loop.queue(() -> transmission.accept(remote, b));

        }, heartbeatPeriod, heartbeatPeriod, TimeUnit.MILLISECONDS);
    }
    private void setMiss(int period) {
        if (missFuture != null) {
            missFuture.cancel(false);
        }
        missFuture = timers.scheduleAtFixedRate(() -> {

            loop.queue(this::doDisconnect);
            LogLine.begin(Logger.Level.Debug).log(remote + "miss").end();

        }, period, period, TimeUnit.MILLISECONDS);
    }

    public void connect() {
        loop.run(() -> {
            clear();
            state = State.Connecting;
            check = (byte) (System.currentTimeMillis() & 0x7F);
            setMiss(connectMissPeriod);

            if (heartbeatFuture != null) {
                heartbeatFuture.cancel(false);
            }
            heartbeatFuture = timers.scheduleAtFixedRate(() -> {

                ByteBuffer b = ByteBuffer.allocate(checkSize + operatorSize);
                b.put(check);
                b.put((byte) Operator.Connect.ordinal());
                b.flip();
                LogLine.begin(Logger.Level.Debug).log("connecting to " + remote).end();
                loop.queue(() -> transmission.accept(remote, b));

            }, 0, connectPeriod, TimeUnit.MILLISECONDS);
        });
    }
    private void doConnect() {
        if (state != State.Connecting) {
            return;
        }
        state = State.Connected;

        LogLine.begin(Logger.Level.Info).log(remote + " " + state).end();
        if (connectCallback != null) {
            connectCallback.accept(this);
        }

        if (!unsent.isEmpty()) {
            doUnsent();
        }
    }
    private void handleConnect() {
        connectConfirm();
        doConnect();
    }
    private void connectConfirm() {
        ByteBuffer b = ByteBuffer.allocate(checkSize + operatorSize);
        b.put(check);
        b.put((byte) Operator.ConnectConfirm.ordinal());
        b.flip();
        transmission.accept(remote, b);
        setHeartbeat();
    }
    private void handleConnectConfirm() {
        doConnect();
    }
    public void message(ByteBuffer b) {
        loop.run(() -> {
            if (state != State.Connected && state != State.Connecting) {
                return;
            }
            ByteBuffer _b = ByteBuffer.wrap(b.array());
            _b.position(b.arrayOffset() + b.position() - headerSize);
            _b.limit(b.arrayOffset() + b.limit());
            _b.mark();
            _b.put(check);
            _b.put((byte) Operator.Message.ordinal());
            _b.putInt(++sentCount);
            _b.reset();
            unsent.addLast(new Datagram(sentCount, _b));
            doUnsent();
        });
    }
    private void handleMessage(int c, ByteBuffer b) {
        if (state != State.Connected) {
            return;
        }
        messageConfirm(c, b);
    }
    private void messageConfirm(int c, ByteBuffer b) {
        ByteBuffer cb = ByteBuffer.allocate(headerSize);
        cb.put(check);
        cb.put((byte) Operator.MessageConfirm.ordinal());
        cb.putInt(c);
        cb.flip();
        transmission.accept(remote, cb);
        setHeartbeat();

        var n = received.head();
        var _i = receivedCount + 1;
        while (_i < c) {
            if (n == null) {
                received.addLast(null);
                n = received.tail();
            }

            ++_i;
            n = n.right();
        }
        if (n == null) {
            received.addLast(b);
        } else {
            n.v = b;
        }

        n = received.head();
        while (n != null) {
            if (n.v == null) {
                break;
            }

            ++receivedCount;
            n = n.right();
        }
        Linked<ByteBuffer> ready;
        if (n == null) {
            ready = received;
            received = new Linked<>();
        } else {
            ready = received.lSplit(n);
        }

        if (!ready.isEmpty() && readCallback != null) {
            readCallback.accept(this, ready);
        }
    }
    private void handleMessageConfirm(int c) {
        if (state != State.Connected) {
            return;
        }
        var n = unconfirmed.head();
        while (n != null) {
            if (c == n.v.c) {
                unconfirmed.split(n);
                LogLine.begin(Logger.Level.Debug).log("receive confirm " + c + " from " + remote).end();
                if (unconfirmed.isEmpty()) {
                    bFlushing = false;
                    if (pardonFuture != null) {
                        pardonFuture.cancel(false);
                    }
                    if (flushedCallback != null) {
                        LogLine.begin(Logger.Level.Info).log("flushed " + remote).end();
                        flushedCallback.accept(this);
                    }
                    if (!unsent.isEmpty()) {
                        doUnsent();
                    }
                }
                break;
            } else if (c < n.v.c) {
                break;
            }
            n = n.right();
        }
    }

    public void disconnect() {
        loop.run(() -> {
            if (state == State.Disconnected || state == State.Disconnecting) {
                return;
            }
            state = State.Disconnecting;
            setMiss(connectMissPeriod);
            if (heartbeatFuture != null) {
                heartbeatFuture.cancel(false);
            }
            heartbeatFuture = timers.scheduleAtFixedRate(() -> {

                ByteBuffer b = ByteBuffer.allocate(checkSize + operatorSize);
                b.put(check);
                b.put((byte) Operator.Disconnect.ordinal());
                b.flip();
                loop.queue(() -> transmission.accept(remote, b));

            }, 0, connectPeriod, TimeUnit.MILLISECONDS);
        });
    }
    public void forceDisconnect() {
        loop.run(() -> {
            if (state == State.Disconnected) {
                return;
            }
            ByteBuffer b = ByteBuffer.allocate(checkSize + operatorSize);
            b.put(check);
            b.put((byte) Operator.Disconnect.ordinal());
            b.flip();
            transmission.accept(remote, b);
            doDisconnect();

        });
    }
    private void handleDisconnect() {
        disconnectConfirm();
        doDisconnect();
    }
    private void disconnectConfirm() {
        ByteBuffer b = ByteBuffer.allocate(checkSize + operatorSize);
        b.put(check);
        b.put((byte) Operator.DisconnectConfirm.ordinal());
        b.flip();
        loop.queue(() -> transmission.accept(remote, b));
        setHeartbeat();
    }
    private void handleDisconnectConfirm() {
        doDisconnect();
    }
    private void doDisconnect() {
        state = State.Disconnected;
        if (missFuture != null) {
            missFuture.cancel(false);
        }
        if (heartbeatFuture != null) {
            heartbeatFuture.cancel(false);
        }
        if (pardonFuture != null) {
            pardonFuture.cancel(false);
        }

        LogLine.begin(Logger.Level.Info).log(remote + " " + state).end();
        if (disconnectCallback != null) {
            disconnectCallback.accept(this);
        }
    }


    public void flush() {
        loop.run(() -> {
            if (state != State.Connected) {
                return;
            }
            unsent.addLast(null);
            doUnsent();
        });
    }
    private void setPardon() {
        if (pardonFuture != null) {
            pardonFuture.cancel(false);
        }
        pardonFuture = timers.scheduleAtFixedRate(() -> {

            loop.queue(() -> {
                var n = unconfirmed.head();
                while (n != null) {
                    transmission.accept(remote, n.v.b.duplicate());
                    n = n.right();
                }
                LogLine.begin(Logger.Level.Debug).log("send pardon to " + remote).end();
            });

        }, pardonPeriod, pardonPeriod, TimeUnit.MILLISECONDS);
    }

    private void clear() {
        check = 0;
        sentCount = 0;
        unsent.clear();
        received.clear();
        receivedCount = 0;
        unconfirmed.clear();

    }

}
