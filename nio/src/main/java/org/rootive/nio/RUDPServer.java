package org.rootive.nio;

import org.rootive.gadget.Linked;
import org.rootive.log.LogLine;
import org.rootive.log.Logger;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Timer;

public class RUDPServer implements Handler {
    @FunctionalInterface
    public interface ReadCallback {
        void invoke(RUDPConnection c, Linked<RUDPConnection.Datagram> l) throws Exception;
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

    private DatagramChannel channel;
    private SelectionKey selectionKey;
    private final Timer timer;
    private final EventLoop eventLoop;
    private final EventLoopThreadPool threads;
    private final LinkedList<Send> unsent = new LinkedList<>();
    private final HashMap<SocketAddress, RUDPConnection> cs = new HashMap<>();

    public RUDPServer(Timer timer, EventLoop eventLoop, int threadsCount) {
        this.timer = timer;
        this.eventLoop = eventLoop;
        threads = new EventLoopThreadPool(threadsCount);
    }

    public void setReadCallback(ReadCallback readCallback) {
        this.readCallback = readCallback;
    }

    public void init(InetSocketAddress local) throws Exception {
        threads.start();
        channel = DatagramChannel.open();
        channel.configureBlocking(false);
        channel.bind(local);
        selectionKey = eventLoop.add(channel, SelectionKey.OP_READ, this);
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
                    c = new RUDPConnection(a, getE(), this::transmission, timer);
                    c.setStateCallback(this::onState);
                    c.setFlushedCallback(this::onFlushed);
                    c.setReadCallback(this::onRead);
                    cs.put(a, c);
                }
                c.handleReceive(b);
            } else {
                break;
            }
        }
    }
    private void handleWrite() throws Exception {
        while (unsent.size() > 0) {
            var s = unsent.removeFirst();
            channel.send(s.b, s.a);
            if (s.b.remaining() > 0) {
                unsent.addFirst(s);
                break;
            }
        }
        if (unsent.size() == 0) {
            selectionKey.interestOpsAnd(~SelectionKey.OP_WRITE);
        }
    }
    private EventLoop getE() {
        if (threads.count() > 0) {
            return threads.get().getEventLoop();
        } else {
            return eventLoop;
        }
    }
    private void onFlushed(RUDPConnection c) {
        LogLine.begin(Logger.Level.Info).log(c + ": flushed").end();
    }
    private void onState(RUDPConnection c) {
        switch (c.getState()) {
            case Connected -> {
                LogLine.begin(Logger.Level.Info).log(c + ": connected").end();
            }
            case Disconnected -> {
                cs.remove(c.getRemote());
                LogLine.begin(Logger.Level.Info).log(c + ": disconnected").end();
            }
        }
    }
    private void onRead(RUDPConnection c, Linked<RUDPConnection.Datagram> l) throws Exception {
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
}
