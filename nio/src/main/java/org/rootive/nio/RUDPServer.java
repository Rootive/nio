package org.rootive.nio;

import org.rootive.gadget.Linked;
import org.rootive.gadget.LoopThreadPool;
<<<<<<< HEAD
=======
import org.rootive.log.LogLine;
import org.rootive.log.Logger;
>>>>>>> cad0642 (将RUDP改良了些并与RPC组合，完成了Peer的总体设计)

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.util.HashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
<<<<<<< HEAD
=======
import java.util.concurrent.locks.ReentrantReadWriteLock;
>>>>>>> cad0642 (将RUDP改良了些并与RPC组合，完成了Peer的总体设计)
import java.util.function.Function;

public class RUDPServer implements Handler {
    @FunctionalInterface public interface ReadCallback {
        void invoke(RUDPConnection c, Linked<ByteBuffer> l) throws Exception;
    }
    @FunctionalInterface public interface Callback {
        void invoke(RUDPConnection c) throws Exception;
<<<<<<< HEAD
    }
    @FunctionalInterface public interface Runner {
        void run(RUDPConnection c) throws Exception;
=======
>>>>>>> cad0642 (将RUDP改良了些并与RPC组合，完成了Peer的总体设计)
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
<<<<<<< HEAD
    private Callback disconnectCallback;
=======
    private Callback stateCallback;
>>>>>>> cad0642 (将RUDP改良了些并与RPC组合，完成了Peer的总体设计)

    private DatagramChannel channel;
    private SelectionKey selectionKey;
    private final ScheduledThreadPoolExecutor timers;
    private final EventLoop eventLoop;
    private final LoopThreadPool threads;
    private final Linked<Send> unsent = new Linked<>();
    private final HashMap<SocketAddress, RUDPConnection> cs = new HashMap<>();
<<<<<<< HEAD
=======
    private final ReentrantReadWriteLock csLock = new ReentrantReadWriteLock();
>>>>>>> cad0642 (将RUDP改良了些并与RPC组合，完成了Peer的总体设计)
    private Function<RUDPConnection, Object> connectionContext = (c) -> null;

    public RUDPServer(ScheduledThreadPoolExecutor timers, EventLoop eventLoop, int threadsCount) {
        this.timers = timers;
        this.eventLoop = eventLoop;
        threads = new LoopThreadPool(threadsCount);
    }

    public void setDisconnectCallback(Callback disconnectCallback) {
        this.disconnectCallback = disconnectCallback;
    }
    public void setReadCallback(ReadCallback readCallback) {
        this.readCallback = readCallback;
    }
<<<<<<< HEAD
=======
    public void setStateCallback(Callback stateCallback) {
        this.stateCallback = stateCallback;
    }
>>>>>>> cad0642 (将RUDP改良了些并与RPC组合，完成了Peer的总体设计)
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

    public void connect(SocketAddress remote) throws Exception {
        eventLoop.run(() -> {
            var c = cs.get(remote);
            if (c == null) {
                c = newConnection(remote);
                c.connect();
                cs.put(remote, c);
            }
        });
    }
    public void run(SocketAddress remote, Runner cr) throws Exception {
        eventLoop.run(() -> {
            var c = cs.get(remote);
            if (c != null) {
                cr.run(c);
            }
        });
    }
    public void force(SocketAddress remote, Runner cr) throws Exception {
        eventLoop.run(() -> {
            var c = cs.get(remote);
            if (c == null) {
                c = newConnection(remote);
                c.connect();
                cs.put(remote, c);
            }
            cr.run(c);
        });
    }

    private RUDPConnection newConnection(SocketAddress a) {
        RUDPConnection ret = new RUDPConnection(a, threads.get().getLoop(), this::transmission, timers);
        ret.setReadCallback(this::onRead);
        ret.setDisconnectCallback(this::onDisconnect);
        ret.context = connectionContext.apply(ret);
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
<<<<<<< HEAD
=======
                    csLock.writeLock().lock();
>>>>>>> cad0642 (将RUDP改良了些并与RPC组合，完成了Peer的总体设计)
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
<<<<<<< HEAD

    private void onDisconnect(RUDPConnection c) throws Exception {
        eventLoop.run(() -> cs.remove(c.getRemote()));
        disconnectCallback.invoke(c);
=======
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
>>>>>>> cad0642 (将RUDP改良了些并与RPC组合，完成了Peer的总体设计)
    }
    private void onRead(RUDPConnection c, Linked<ByteBuffer> l) throws Exception {
        if (readCallback != null) {
            readCallback.invoke(c, l);
        }
    }
    private void transmission(SocketAddress a, ByteBuffer b) throws Exception {
        eventLoop.run(() -> {
            var s = new Send(a, b);
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
