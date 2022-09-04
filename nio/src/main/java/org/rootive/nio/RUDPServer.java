package org.rootive.nio;

import org.rootive.util.Linked;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.util.HashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class RUDPServer implements Handler {
    static public class Send {
        public SocketAddress a;
        public ByteBuffer b;
        public Send(SocketAddress a, ByteBuffer b) {
            this.a = a;
            this.b = b;
        }
    }

    private BiConsumer<RUDPConnection, Linked<ByteBuffer>> readCallback;
    private Consumer<RUDPConnection> disconnectCallback;

    private DatagramChannel channel;
    private SelectionKey selectionKey;
    private final ScheduledThreadPoolExecutor timers;
    private final EventLoop eventLoop;
    private final LoopThreadPool threads;
    private final Linked<Send> unsent = new Linked<>();
    private final HashMap<SocketAddress, RUDPConnection> cs = new HashMap<>();
    private Function<RUDPConnection, Object> contextSetter = (c) -> null;

    public RUDPServer(EventLoop eventLoop, int timersCount, int threadsCount) {
        this.eventLoop = eventLoop;
        timers = new ScheduledThreadPoolExecutor(timersCount);
        threads = new LoopThreadPool(threadsCount);
    }

    public void setDisconnectCallback(Consumer<RUDPConnection> disconnectCallback) {
        this.disconnectCallback = disconnectCallback;
    }
    public void setReadCallback(BiConsumer<RUDPConnection, Linked<ByteBuffer>> readCallback) {
        this.readCallback = readCallback;
    }
    public void setContextSetter(Function<RUDPConnection, Object> contextSetter) {
        this.contextSetter = contextSetter;
    }

    public void init(InetSocketAddress local) throws InterruptedException, IOException {
        threads.start();
        channel = DatagramChannel.open();
        channel.configureBlocking(false);
        channel.bind(local);
        selectionKey = eventLoop.add(channel, SelectionKey.OP_READ, this);
    }

    public void connect(SocketAddress remote) {
        eventLoop.run(() -> {
            var c = cs.get(remote);
            if (c == null) {
                c = newConnection(remote);
                c.connect();
                cs.put(remote, c);
            }
        });
    }
    public void run(SocketAddress remote, Consumer<RUDPConnection> cr) {
        eventLoop.run(() -> {
            var c = cs.get(remote);
            if (c != null) {
                cr.accept(c);
            }
        });
    }
    public void force(SocketAddress remote, Consumer<RUDPConnection> cr) {
        eventLoop.run(() -> {
            var c = cs.get(remote);
            if (c == null) {
                c = newConnection(remote);
                c.connect();
                cs.put(remote, c);
            }
            cr.accept(c);
        });
    }
    public void foreach(Consumer<RUDPConnection> cr) {
        eventLoop.run(() -> cs.forEach((k, v) -> cr.accept(v)));
    }

    private RUDPConnection newConnection(SocketAddress a) {
        RUDPConnection ret = new RUDPConnection(a, threads.get().getLoop(), this::transmission, timers);
        ret.setReadCallback(this::onRead);
        ret.setDisconnectCallback(this::onDisconnect);
        ret.context = contextSetter.apply(ret);
        return ret;
    }

    @Override
    public void handleEvent() {
        if (selectionKey.isReadable()) {
            handleRead();
        } else if (selectionKey.isWritable()) {
            handleWrite();
        }
    }
    private void handleRead() {
        while (true) {
            ByteBuffer b = ByteBuffer.allocate(RUDPConnection.MTU);
            SocketAddress a = null;
            try {
                a = channel.receive(b);
            } catch (IOException e) {
                e.printStackTrace();
            }
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
    private void handleWrite() {
        while (!unsent.isEmpty()) {
            var s = unsent.removeFirst();
            try {
                channel.send(s.b, s.a);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (s.b.remaining() > 0) {
                unsent.addFirst(s);
                break;
            }
        }
        if (unsent.isEmpty()) {
            selectionKey.interestOpsAnd(~SelectionKey.OP_WRITE);
        }
    }

    private void onDisconnect(RUDPConnection c) {
        eventLoop.run(() -> cs.remove(c.getRemote()));
        disconnectCallback.accept(c);
    }
    private void onRead(RUDPConnection c, Linked<ByteBuffer> l) {
        if (readCallback != null) {
            readCallback.accept(c, l);
        }
    }
    private void transmission(SocketAddress a, ByteBuffer b) {
        eventLoop.run(() -> {
            var s = new Send(a, b);
            if (unsent.isEmpty()) {
                try {
                    channel.send(s.b, s.a);
                } catch (IOException e) {
                    e.printStackTrace();
                }
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
