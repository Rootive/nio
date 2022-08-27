package org.rootive.nio;

import org.rootive.gadget.Linked;
import org.rootive.gadget.LoopThreadPool;
import org.rootive.log.LogLine;
import org.rootive.log.Logger;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.util.HashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

public class RUDPServer implements Handler {
    @FunctionalInterface public interface ReadCallback {
        void invoke(RUDPConnection c, Linked<ByteBuffer> l) throws Exception;
    }
    @FunctionalInterface public interface Callback {
        void invoke(RUDPConnection c) throws Exception;
    }
    static public class Send {
        public SocketAddress a;
        public ByteBuffer b;
        public Send(SocketAddress a, ByteBuffer b) {
            this.a = a;
            this.b = b;
        }
    }

    private ReadCallback readCallback;
    private Callback stateCallback;

    private DatagramChannel channel;
    private SelectionKey selectionKey;
    private final ScheduledThreadPoolExecutor timers;
    private final EventLoop eventLoop;
    private final LoopThreadPool threads;
    private final Linked<Send> unsent = new Linked<>();
    private final HashMap<SocketAddress, RUDPConnection> cs = new HashMap<>();
    private final ReentrantReadWriteLock csLock = new ReentrantReadWriteLock();
    private Function<RUDPConnection, Object> connectionContext = (c) -> null;

    public RUDPServer(ScheduledThreadPoolExecutor timers, EventLoop eventLoop, int threadsCount) {
        this.timers = timers;
        this.eventLoop = eventLoop;
        threads = new LoopThreadPool(threadsCount);
    }

    public void setReadCallback(ReadCallback readCallback) {
        this.readCallback = readCallback;
    }
    public void setStateCallback(Callback stateCallback) {
        this.stateCallback = stateCallback;
    }
    public void setConnectionContext(Function<RUDPConnection, Object> connectionContext) {
        this.connectionContext = connectionContext;
    }

    public void init(InetSocketAddress local) throws Exception {
        threads.start();
        channel = DatagramChannel.open();
        channel.configureBlocking(false);
        channel.bind(local);
        selectionKey = eventLoop.add(channel, SelectionKey.OP_READ, this);
    }
    public RUDPConnection get(SocketAddress remote) throws Exception {
        csLock.readLock().lock();
        var ret = cs.get(remote);
        csLock.readLock().unlock();
        if (ret == null) {
            ret = newConnection(remote);
            ret.init();
            csLock.writeLock().lock();
            cs.put(remote, ret);
            csLock.writeLock().unlock();
        }
        return ret;
    }

    @Override
    public void handleEvent() throws Exception {
        if (selectionKey.isReadable()) {
            handleRead();
        } else if (selectionKey.isWritable()) {
            handleWrite();
        }
    }
    private void handleRead() throws Exception {
        while (true) {
            ByteBuffer b = ByteBuffer.allocate(RUDPConnection.MTU);
            var a = channel.receive(b);
            if (a != null) {
                b.flip();
                csLock.readLock().lock();
                var c = cs.get(a);
                csLock.readLock().unlock();
                if (c == null) {
                    c = newConnection(a);
                    csLock.writeLock().lock();
                    cs.put(a, c);
                    csLock.writeLock().unlock();
                }
                c.handleReceive(b);
            } else {
                break;
            }
        }
    }
    private void handleWrite() throws Exception {
        while (!unsent.isEmpty()) {
            var s = unsent.removeFirst();
            channel.send(s.b, s.a);
            if (s.b.remaining() > 0) {
                unsent.addFirst(s);
                break;
            }
        }
        if (unsent.isEmpty()) {
            selectionKey.interestOpsAnd(~SelectionKey.OP_WRITE);
        }
    }
    private void onFlushed(RUDPConnection c) {
        LogLine.begin(Logger.Level.Info).log(c + ": flushed").end();
    }
    private void onState(RUDPConnection c) throws Exception {
        switch (c.getState()) {
            case Connected -> LogLine.begin(Logger.Level.Info).log(c + ": connected").end();
            case Disconnected -> {
                csLock.writeLock().lock();
                cs.remove(c.getRemote());
                csLock.writeLock().unlock();
                LogLine.begin(Logger.Level.Info).log(c + ": disconnected").end();
            }
        }
        if (stateCallback != null) {
            stateCallback.invoke(c);
        }
    }
    private void onRead(RUDPConnection c, Linked<ByteBuffer> l) throws Exception {
        if (readCallback != null) {
            readCallback.invoke(c, l);
        }
    }
    private void transmission(SocketAddress a, ByteBuffer b) throws Exception {
        var s = new Send(a, b);
        eventLoop.run(() -> {
            if (unsent.isEmpty()) {
                channel.send(s.b, s.a);
            }
            if (s.b.remaining() > 0) {
                unsent.addLast(s);
                if (!selectionKey.isWritable()) {
                    selectionKey.interestOpsOr(SelectionKey.OP_WRITE);
                }
            }
        });
    }
    private RUDPConnection newConnection(SocketAddress a) {
        RUDPConnection ret = new RUDPConnection(a, threads.get().getLoop(), this::transmission, timers);
        ret.setStateCallback(this::onState);
        ret.setFlushedCallback(this::onFlushed);
        ret.setReadCallback(this::onRead);
        ret.context = connectionContext.apply(ret);
        return ret;
    }
}
