package org.rootive.nio;

import org.rootive.gadgets.Linked;
import org.rootive.gadgets.LoopThreadPool;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.util.HashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.function.Function;

public class RUDPServer implements Handler {
    @FunctionalInterface public interface ReadCallback {
        void invoke(RUDPConnection c, Linked<ByteBuffer> l) throws Exception;
    }
    @FunctionalInterface public interface Callback {
        void invoke(RUDPConnection c) throws Exception;
    }
    @FunctionalInterface public interface Runner {
        void run(RUDPConnection c) throws Exception;
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
    private Callback disconnectCallback;

    private DatagramChannel channel;
    private SelectionKey selectionKey;
    private final ScheduledThreadPoolExecutor timers;
    private final EventLoop eventLoop;
    private final LoopThreadPool threads;
    private final Linked<Send> unsent = new Linked<>();
    private final HashMap<SocketAddress, RUDPConnection> cs = new HashMap<>();
    private Function<RUDPConnection, Object> contextSetter = (c) -> null;

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
    public void setContextSetter(Function<RUDPConnection, Object> contextSetter) {
        this.contextSetter = contextSetter;
    }

    public void init(InetSocketAddress local) throws Exception {
        threads.start();
        channel = DatagramChannel.open();
        channel.configureBlocking(false);
        channel.bind(local);
        selectionKey = eventLoop.add(channel, SelectionKey.OP_READ, this);
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
    public void foreach(Runner cr) throws Exception {
        eventLoop.run(() -> cs.forEach((k, v) -> {
            try {
                cr.run(v);
            } catch (Exception e) {
                eventLoop.handleException(e);
            }
        }));
    }

    private RUDPConnection newConnection(SocketAddress a) {
        RUDPConnection ret = new RUDPConnection(a, threads.get().getLoop(), this::transmission, timers);
        ret.setReadCallback(this::onRead);
        ret.setDisconnectCallback(this::onDisconnect);
        ret.context = contextSetter.apply(ret);
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
                var c = cs.get(a);
                if (c == null) {
                    c = newConnection(a);
                    cs.put(a, c);
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

    private void onDisconnect(RUDPConnection c) throws Exception {
        eventLoop.run(() -> cs.remove(c.getRemote()));
        disconnectCallback.invoke(c);
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
}
