package org.rootive.nio;

import org.rootive.gadget.Linked;
import org.rootive.gadget.Loop;
import org.rootive.log.LogLine;
import org.rootive.log.Logger;

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class RUDPConnection {
    @FunctionalInterface public interface Callback {
        void invoke(RUDPConnection c) throws Exception;
    }
    @FunctionalInterface public interface ReadCallback {
        void invoke(RUDPConnection c, Linked<ByteBuffer> l) throws Exception;
    }
    @FunctionalInterface public interface Transmission {
        void accept(SocketAddress a, ByteBuffer b) throws Exception;
    }

    static public class Datagram {
        public int c;
        public ByteBuffer b;

        public Datagram(int c, ByteBuffer b) {
            this.c = c;
            this.b = b;
        }
    }

    public enum State {
        Connecting, Connected, Disconnected
    }

    public static final int MTU = 548;
    private static final int checkSize = 4;
    private static final int countSize = 4;
    public static final int headerSize = checkSize + countSize;
    static private final int missLine = 2;
    static private final int heartbeatPeriod = 8000;
    static private final int pardonPeriod = 2000;

    private final SocketAddress remote;
    private final Loop loop;

    private final Transmission transmission;
    private ReadCallback readCallback;
    private Callback stateCallback;
    private Callback flushedCallback;

    private int missCount;
    private final ScheduledThreadPoolExecutor timers;
    private ScheduledFuture<?> missFuture;
    private ScheduledFuture<?> heartbeatFuture;
    private ScheduledFuture<?> pardonFuture;

    private State state = State.Disconnected;
    private final ReentrantReadWriteLock stateLock = new ReentrantReadWriteLock();
    private final Lock stateReadLock = stateLock.readLock();
    private final Lock stateWriteLock = stateLock.writeLock();
    private boolean bFlushing;
    private int check;
    private int sentCount;

    private Linked<Datagram> unsent = new Linked<>();
    private Linked<ByteBuffer> received = new Linked<>();
    private final Linked<Datagram> unconfirmed = new Linked<>();
    private int receivedCount;

    public Object context;

    public RUDPConnection(SocketAddress remote, Loop loop, Transmission transmission, ScheduledThreadPoolExecutor timers) {
        this.remote = remote;
        this.loop = loop;
        this.transmission = transmission;
        this.timers = timers;
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

    private void _init() {
        if (getState() == State.Disconnected) {
            clear();
            setState(State.Connecting);
            startMiss();
            check = (int) System.currentTimeMillis() & 0x1111111;
            startHeartbeat(0);
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
        if (b.remaining() >= checkSize) {
            startMiss();
            switch (getState()) {
                case Connecting -> {
                    setState(State.Connected);
                    if (stateCallback != null) {
                        stateCallback.invoke(this);
                    }
                    if (!unsent.isEmpty()) {
                        handleUnsent();
                    }
                }
                case Disconnected -> {
                    clear();
                    setState(State.Connected);
                    startHeartbeat(0);
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
                if (b.remaining() >= countSize) {
                    var count = b.getInt();
                    if (b.remaining() > 0) {
                        handleMessage(count, b);
                    } else if (!unconfirmed.isEmpty()) {
                        handleConfirm(count);
                    }
                }
            } else {
                startHeartbeat(0);
            }
        }
    }

    public void init() throws Exception {
        loop.run(this::_init);
    }
    public void flush() throws Exception {
        loop.run(this::_flush);
    }
    public void message(ByteBuffer b) throws Exception {
        loop.run(() -> _message(b));
    }

    void handleReceive(ByteBuffer b) throws Exception {
        loop.run(() -> _handleReceive(b));
    }
    private void handleMessage(int c, ByteBuffer b) throws Exception {
        confirm(c);
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
            readCallback.invoke(this, ready);
        }
    }
    private void handleConfirm(int c) throws Exception {
        var n = unconfirmed.head();
        while (n != null) {
            if (c == n.v.c) {
                unconfirmed.split(n);
                LogLine.begin(Logger.Level.Debug).log("received confirm: " + c).end();
                if (unconfirmed.isEmpty()) {
                    bFlushing = false;
                    if (pardonFuture != null) {
                        pardonFuture.cancel(false);
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
        }
    }

    private void handleUnsent() throws Exception {
        if (bFlushing || getState() != State.Connected) {
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

            startHeartbeat(heartbeatPeriod);
        }

        if (!unsent.isEmpty() && unsent.head().v == null && !unconfirmed.isEmpty()) {
            unsent.removeFirst();
            bFlushing = true;
            startPardon();
        }

    }
    private void confirm(int count) throws Exception {
        ByteBuffer b = ByteBuffer.allocate(headerSize);
        b.putInt(check);
        b.putInt(count);
        b.flip();

        transmission.accept(remote, b);
        startHeartbeat(heartbeatPeriod);
    }
    private void startHeartbeat(int delay) {
        if (heartbeatFuture != null) {
            heartbeatFuture.cancel(false);
        }
        heartbeatFuture = timers.scheduleAtFixedRate(() -> {

            ByteBuffer b = ByteBuffer.allocate(checkSize);
            b.putInt(check);
            b.flip();
            LogLine.begin(Logger.Level.Debug).log("send heartbeat").end();
            loop.queue(() -> transmission.accept(remote, b));

        }, delay, heartbeatPeriod, TimeUnit.MILLISECONDS);
    }
    private void startMiss() {
        if (missFuture != null) {
            missFuture.cancel(false);
        }
        missCount = 0;
        missFuture = timers.scheduleAtFixedRate(() -> {

            if (missCount == missLine) {
                setState(State.Disconnected);
                loop.queue(() -> {
                    if (missFuture != null) {
                        missFuture.cancel(false);
                    }
                    if (heartbeatFuture != null) {
                        heartbeatFuture.cancel(false);
                    }
                    if (pardonFuture != null) {
                        pardonFuture.cancel(false);
                    }
                    if (stateCallback != null) {
                        stateCallback.invoke(RUDPConnection.this);
                    }
                });
            }
            ++missCount;
            LogLine.begin(Logger.Level.Debug).log("miss " + missCount).end();

        }, heartbeatPeriod, heartbeatPeriod, TimeUnit.MILLISECONDS);
    }
    private void startPardon() {
        if (pardonFuture != null) {
            pardonFuture.cancel(false);
        }
        pardonFuture = timers.scheduleAtFixedRate(() -> {

            LogLine.begin(Logger.Level.Debug).log("send pardon").end();
            loop.queue(() -> {
                var n = unconfirmed.head();
                while (n != null) {
                    transmission.accept(remote, n.v.b.duplicate());
                    n = n.right();
                }
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

}
