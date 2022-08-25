package org.rootive.nio;

import org.rootive.gadget.Linked;
import org.rootive.log.LogLine;
import org.rootive.log.Logger;

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class RUDPConnection {
    @FunctionalInterface
    public interface Callback {
        void invoke(RUDPConnection c) throws Exception;
    }
    @FunctionalInterface
    public interface ReadCallback {
        void invoke(RUDPConnection c, Linked<Datagram> l) throws Exception;
    }
    @FunctionalInterface
    public interface Transmission {
        void accept(SocketAddress a, ByteBuffer b) throws Exception;
    }

    static public class Datagram {
        public int c;
        public ByteBuffer b;

        public Datagram(int c, ByteBuffer b) {
            this.c = c;
            this.b = b;
        }
        public Datagram(int c) {
            this.c = c;
        }
    }

    public enum State {
        Connecting, Connected, Disconnected
    }

    public static final int MTU = 548;
    private static final int checkSize = 4;
    private static final int countSize = 4;
    private static final int headerSize = checkSize + countSize;
    static private final int missLine = 2;
    static private final int heartbeatPeriod = 8000;
    static private final int pardonPeriod = 3000;

    private final SocketAddress remote;
    private final EventLoop eventLoop;

    private final Transmission transmission;
    private Callback stateCallback;
    private ReadCallback readCallback;
    private Callback flushedCallback;

    private final Timer timer;
    private int missCount;
    private TimerTask miss;
    private TimerTask heartbeat;
    private TimerTask pardon;

    private State state = State.Disconnected;
    private boolean bFlushing;
    private final ReentrantReadWriteLock stateLock = new ReentrantReadWriteLock();
    private final Lock stateReadLock = stateLock.readLock();
    private final Lock stateWriteLock = stateLock.writeLock();
    private int check;
    private int sentCount;

    private Linked<Datagram> unsent = new Linked<>();
    private Linked<Datagram> received = new Linked<>();
    private final Linked<Datagram> unconfirmed = new Linked<>();

    public RUDPConnection(SocketAddress remote, EventLoop eventLoop, Transmission transmission, Timer timer) {
        this.remote = remote;
        this.eventLoop = eventLoop;
        this.transmission = transmission;
        this.timer = timer;
    }

    public SocketAddress getRemote() {
        return remote;
    }
    public void setReadCallback(ReadCallback readCallback) {
        this.readCallback = readCallback;
    }
    public void setStateCallback(Callback stateCallback) {
        this.stateCallback = stateCallback;
    }
    public void setFlushedCallback(Callback flushedCallback) {
        this.flushedCallback = flushedCallback;
    }

    public static ByteBuffer newByteBuffer(int c) {
        var buffer = ByteBuffer.allocate(c + headerSize);
        return buffer.slice(headerSize, c);
    }
    public static ByteBuffer newByteBuffer() {
        var buffer = ByteBuffer.allocate(MTU);
        return buffer.slice(headerSize, MTU - headerSize);
    }

    private void _connect() {
        if (getState() == State.Disconnected) {
            clear();
            setState(State.Connecting);
            miss();
            check = (int) System.currentTimeMillis();
            heartbeat(0);
        }
    }
    private void _flush() throws Exception {
        if (getState() != State.Disconnected) {
            unsent.addLast(null);
            handleUnsent();
        }
    }
    private void _message(ByteBuffer b) throws Exception {
        if (getState() != State.Disconnected) {
            ByteBuffer _b = ByteBuffer.wrap(b.array());
            _b.position(b.arrayOffset() + b.position() - headerSize);
            _b.limit(b.arrayOffset() + b.limit());
            _b.mark();
            _b.putInt(check);
            _b.putInt(++sentCount);
            _b.reset();
            unsent.addLast(new Datagram(sentCount, _b));
            handleUnsent();

        }
    }
    private void _handleReceive(ByteBuffer b) throws Exception {
        if (b.remaining() > checkSize) {
            miss();
            switch (getState()) {
                case Connecting -> {
                    setState(State.Connected);
                    if (stateCallback != null) {
                        stateCallback.invoke(this);
                    }
                    handleUnsent();
                }
                case Disconnected -> {
                    clear();
                    setState(State.Connected);
                    heartbeat(0);
                    if (stateCallback != null) {
                        stateCallback.invoke(this);
                    }
                }
            }

            var c = b.getInt();
            if (c > check) {
                clear();
                check = c;
            }
            if (c >= check) {
                if (b.remaining() > countSize) {
                    var count = b.getInt();
                    if (b.remaining() > 0) {
                        handleMessage(count, b);
                    } else {
                        handleConfirm(count);
                    }
                }
            } else {
                heartbeat(0);
            }
        }
    }

    public void connect() throws Exception {
        eventLoop.run(this::_connect);
    }
    public void flush() throws Exception {
        eventLoop.run(this::_flush);
    }
    public void message(ByteBuffer b) throws Exception {
        eventLoop.run(() -> _message(b));
    }
    public void handleReceive(ByteBuffer b) throws Exception {
        eventLoop.run(() -> _handleReceive(b));
    }

    private void handleMessage(int c, ByteBuffer b) throws Exception {
        confirm(c);
        if (c >= received.head().v.c) {
            var tar = received.head().find((d) -> d.c == c);
            if (tar != null) {
                tar.v.b = b;

                Linked<Datagram> ready;
                var n = received.head().find((d) -> d.b == null);
                if (n == null) {
                    ready = received;
                    received = new Linked<>();
                    received.addLast(new Datagram(ready.tail().v.c + 1));
                } else {
                    ready = received.lSplit(n);
                }
                if (!ready.isEmpty() && readCallback != null) {
                    readCallback.invoke(this, ready);
                }
            } else {
                for (var _i = received.tail().v.c + 1; _i <= c; ++_i) {
                    received.addLast(new Datagram(_i));
                }
                received.tail().v.b = b;
            }
        }
    }
    private void handleConfirm(int c) throws Exception {
        var n = unconfirmed.head();
        if (n != null && c >= n.v.c) {
            do {
                if (c == n.v.c) {
                    unconfirmed.split(n);
                    if (unconfirmed.isEmpty()) {
                        bFlushing = false;
                        if (pardon != null) {
                            pardon.cancel();
                        }
                        if (flushedCallback != null) {
                            flushedCallback.invoke(this);
                        }
                        if (!unsent.isEmpty()) {
                            handleUnsent();
                        }
                    }
                    break;
                } else if (c < n.v.c) {
                    break;
                }
                n = n.right();
            } while (n != null);
        }
    }

    private void handleUnsent() throws Exception {
        if (bFlushing) {
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

            heartbeat(heartbeatPeriod);
        }

        if (!unsent.isEmpty() && unsent.head().v == null && !unconfirmed.isEmpty()) {
            unsent.removeFirst();
            bFlushing = true;
            pardon = newPardon();
            timer.schedule(pardon, 0, pardonPeriod);
        }

    }
    private void confirm(int count) throws Exception {
        ByteBuffer b = ByteBuffer.allocate(headerSize);
        b.putInt(check);
        b.putInt(count);
        b.flip();

        transmission.accept(remote, b);
        heartbeat(heartbeatPeriod);
    }
    private void heartbeat(long delay) {
        if (heartbeat != null) {
            heartbeat.cancel();
        }
        heartbeat = newHeartbeat();
        timer.schedule(heartbeat, delay, heartbeatPeriod);
    }
    private void miss() {
        if (miss != null) {
            miss.cancel();
        }
        missCount = 0;
        miss = newMiss();
        timer.schedule(miss, heartbeatPeriod, heartbeatPeriod);
    }
    private void clear() {
        check = 0;
        sentCount = 0;
        unsent.clear();
        received.clear();
        received.addLast(new Datagram(1));
        unconfirmed.clear();

    }

    public State getState() {
        stateReadLock.lock();
        var ret = state;
        stateReadLock.unlock();
        return ret;
    }
    private void setState(State v) {
        stateWriteLock.lock();
        state = v;
        stateWriteLock.unlock();
    }

    private TimerTask newMiss() {
        return new TimerTask() {
            @Override
            public void run() {
                if (missCount == missLine) {
                    timer.purge();
                    setState(State.Disconnected);
                    eventLoop.queue(() -> {
                        if (miss != null) {
                            miss.cancel();
                        }
                        if (heartbeat != null) {
                            heartbeat.cancel();
                        }
                        if (pardon != null) {
                            pardon.cancel();
                        }
                        if (stateCallback != null) {
                            stateCallback.invoke(RUDPConnection.this);
                        }
                    });
                }
                ++missCount;
            }
        };
    }
    private TimerTask newHeartbeat() {
        return new TimerTask() {
            @Override
            public void run() {
                ByteBuffer b = ByteBuffer.allocate(checkSize);
                b.putInt(check);
                b.flip();
                eventLoop.queue(() -> transmission.accept(remote, b));
            }
        };
    }
    private TimerTask newPardon() {
        return new TimerTask() {
            @Override
            public void run() {
                eventLoop.queue(() -> {
                    var n = unconfirmed.head();
                    do {
                        transmission.accept(remote, n.v.b.duplicate());
                        n = n.right();
                    } while (n != null);
                });
            }
        };
    }

}
