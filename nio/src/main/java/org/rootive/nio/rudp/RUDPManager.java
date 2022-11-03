package org.rootive.nio.rudp;

import org.rootive.nio.EventLoopThreadPool;
import org.rootive.nio.Handler;
import org.rootive.nio.PlainEventLoop;
import org.rootive.nio.SelectEventLoop;
import org.rootive.util.Linked;

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
    public record Vector(SocketAddress socketAddress, ByteBuffer byteBuffer) { }
    static public int connectionPeriod = 50;
    static public long lastReceiveLine = 8 * 1000;

    private SocketAddress local;
    private DatagramChannel channel;
    private SelectionKey selectionKey;

    private final SelectEventLoop selectEventLoop;
    private final EventLoopThreadPool eventLoopThreadPool;
    private final Timer timer = new Timer();

    private final Linked<Vector> unsent = new Linked<>();
    private final HashMap<SocketAddress, RUDPConnection> connections = new HashMap<>();

    private BiConsumer<RUDPConnection, Linked<ByteBuffer>> readCallback;
    private Consumer<RUDPConnection> resetCallback;
    private Function<RUDPConnection, Object> contextSetter = (rudpConnection) -> null;

    public RUDPManager(SelectEventLoop selectEventLoop, int threadsCount) {
        this.selectEventLoop = selectEventLoop;
        eventLoopThreadPool = new EventLoopThreadPool(threadsCount, PlainEventLoop.class);
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
        eventLoopThreadPool.start();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                selectEventLoop.run(() -> {
                    ArrayList<SocketAddress> delete = new ArrayList<>();
                    var currentTimeMillis = System.currentTimeMillis();
                    for (var entry : connections.entrySet()) {
                        var connection = entry.getValue();
                        if (currentTimeMillis - connection.lastReceive >= lastReceiveLine) {
                            System.out.println("timeout");
                            delete.add(entry.getKey());
                            continue;
                        }
                        connection.next();
                    }
                    for (var socketAddress : delete) {
                        var connection = connections.remove(socketAddress);
                        connection.getEventLoop().run(connection::reset);
                    }
                });
            }
        }, connectionPeriod, connectionPeriod);

        channel = DatagramChannel.open();
        channel.configureBlocking(false);
        channel.bind(local);
        selectionKey = selectEventLoop.add(channel, SelectionKey.OP_READ, this);
    }

    private RUDPConnection newConnection(SocketAddress remote) {
        RUDPConnection ret = new RUDPConnection(this, local, remote, eventLoopThreadPool.get().getEventLoop());
        ret.setReadCallback(readCallback);
        ret.setResetCallback(resetCallback);
        ret.context = contextSetter.apply(ret);
        return ret;
    }

    public void force(SocketAddress remote, Consumer<RUDPConnection> consumer) {
        selectEventLoop.run(() -> {
            var rudpConnection = connections.get(remote);
            if (rudpConnection == null) {
                rudpConnection = newConnection(remote);
                rudpConnection.lastReceive = System.currentTimeMillis();
                connections.put(remote, rudpConnection);
            }
            RUDPConnection finalC = rudpConnection;
            rudpConnection.getEventLoop().run(() -> consumer.accept(finalC));
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
                channel.send(s.byteBuffer, s.socketAddress);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (s.byteBuffer.remaining() > 0) {
                unsent.addFirst(s);
                break;
            }
        }
        if (unsent.isEmpty()) {
            selectionKey.interestOpsAnd(~SelectionKey.OP_WRITE);
        }
    }

    void send(RUDPConnection connection, ByteBuffer byteBuffer) {
        selectEventLoop.run(() -> {
            var vector = new Vector(connection.getRemote(), byteBuffer);
            if (unsent.isEmpty()) {
                try {
                    channel.send(vector.byteBuffer, vector.socketAddress);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (vector.byteBuffer.remaining() > 0) {
                unsent.addLast(vector);
                if (!selectionKey.isWritable()) {
                    selectionKey.interestOpsOr(SelectionKey.OP_WRITE);
                }
            }
        });
    }
}
