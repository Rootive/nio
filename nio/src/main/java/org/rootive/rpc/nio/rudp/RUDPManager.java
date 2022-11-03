package org.rootive.rpc.nio.rudp;

import org.rootive.rpc.nio.EventLoop;
import org.rootive.rpc.nio.Handler;
import org.rootive.rpc.nio.LoopThreadPool;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class RUDPManager implements Handler {
    static public record Vector(SocketAddress a, ByteBuffer b) { }
    static public int connectionPeriod = 40;
    static public long lastReceiveLine = 8 * 1000;

    private SocketAddress local;
    private DatagramChannel channel;
    private SelectionKey selectionKey;

    private final EventLoop eventLoop;
    private final LoopThreadPool connectionThreads;
    private final Timer connectionTimer = new Timer();

    private final Linked<Vector> unsent = new Linked<>();
    private final HashMap<SocketAddress, RUDPConnection> connections = new HashMap<>();

    private BiConsumer<RUDPConnection, Linked<ByteBuffer>> readCallback;
    private Consumer<RUDPConnection> resetCallback;
    private Function<RUDPConnection, Object> contextSetter = (c) -> null;

    public RUDPManager(EventLoop eventLoop, int threadsCount) {
        this.eventLoop = eventLoop;
        connectionThreads = new LoopThreadPool(threadsCount);
    }

    public void setReadCallback(BiConsumer<RUDPConnection, Linked<ByteBuffer>> readCallback) {
        this.readCallback = readCallback;
    }
    public void setResetCallback(Consumer<RUDPConnection> resetCallback) {
        this.resetCallback = resetCallback;
    }
    public void setContextSetter(Function<RUDPConnection, Object> contextSetter) {
        this.contextSetter = contextSetter;
    }

    public void init(InetSocketAddress local) throws InterruptedException, IOException {
        this.local = local;
        connectionThreads.start();
        connectionTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                eventLoop.run(() -> {
                    ArrayList<SocketAddress> delete = new ArrayList<>();
                    var currentTimeMillis = System.currentTimeMillis();
                    for (var v : connections.entrySet()) {
                        var c = v.getValue();
                        if (currentTimeMillis - c.lastReceive >= lastReceiveLine) {
                            System.out.println("timeout");
                            delete.add(v.getKey());
                            continue;
                        }
                        c.next();
                    }
                    for (var a : delete) {
                        var c = connections.remove(a);
                        c.getLoop().run(c::reset);
                    }
                });
            }
        }, connectionPeriod, connectionPeriod);

        channel = DatagramChannel.open();
        channel.configureBlocking(false);
        channel.bind(local);
        selectionKey = eventLoop.add(channel, SelectionKey.OP_READ, this);
    }

    private RUDPConnection newConnection(SocketAddress remote) {
        RUDPConnection ret = new RUDPConnection(this, local, remote, connectionThreads.get().getLoop());
        ret.setReadCallback(readCallback);
        ret.setResetCallback(resetCallback);
        ret.context = contextSetter.apply(ret);
        return ret;
    }
    public void force(SocketAddress remote, Consumer<RUDPConnection> cr) {
        eventLoop.run(() -> {
            var c = connections.get(remote);
            if (c == null) {
                c = newConnection(remote);
                c.lastReceive = System.currentTimeMillis();
                connections.put(remote, c);
            }
            RUDPConnection finalC = c;
            connectionThreads.get().getLoop().run(() -> cr.accept(finalC));
        });
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
            ByteBuffer b = ByteBuffer.allocate(Constexpr.MTU);
            SocketAddress a = null;
            try {
                a = channel.receive(b);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (a != null) {
                b.flip();
                var c = connections.get(a);
                if (c == null) {
                    c = newConnection(a);
                    connections.put(a, c);
                }
                c.lastReceive = System.currentTimeMillis();
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

    void send(RUDPConnection a, ByteBuffer b) {
        eventLoop.run(() -> {
            var s = new Vector(a.getRemote(), b);
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
