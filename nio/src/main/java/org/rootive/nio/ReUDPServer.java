package org.rootive.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.util.*;

public class ReUDPServer implements Handler {
    static class I {
        int i;
        public I(int i) {
            this.i = i;
        }
    }
    @FunctionalInterface
    public interface ReadCallback {
        void accept(SocketAddress a, ByteBuffer b) throws Exception;
    }
    @FunctionalInterface
    public interface Callback {
        void accept(Send s) throws Exception;
    }

    static public class Send {
        public SocketAddress a;
        public ByteBuffer b;
        public int i;
        public int re;
        public Send(SocketAddress a, ByteBuffer b, int i) {
            this.a = a;
            this.b = b;
            this.i = i;
        }
    }

    private static final int bufferSize = 548; // MTU
    private static final int confirmPeriod = 1000;
    private static final int reCount = 3;

    private int count;
    private DatagramChannel channel;
    private SelectionKey selectionKey;
    private final EventLoop eventLoop;
    private final LinkedList<Send> unsent = new LinkedList<>();
    private final LinkedList<Send> unconfirmed = new LinkedList<>(); // BUG Rootive: 链表会是最好的选择吗？
    private final HashMap<SocketAddress, I> received = new HashMap<>();
    private final Timer retransmission = new Timer();

    private ReadCallback readCallback;
    private Callback dropCallback;
    private Callback confirmedCallback;

    public ReUDPServer(EventLoop eventLoop) {
        this.eventLoop = eventLoop;
    }

    public void setReadCallback(ReadCallback readCallback) {
        this.readCallback = readCallback;
    }
    public void setDropCallback(Callback dropCallback) {
        this.dropCallback = dropCallback;
    }
    public void setConfirmedCallback(Callback confirmedCallback) {
        this.confirmedCallback = confirmedCallback;
    }

    static public ByteBuffer newBuffer() {
        var buffer = ByteBuffer.allocate(bufferSize);
        return buffer.slice(4, bufferSize - 4);
    }

    public void init(InetSocketAddress local) throws IOException {
        channel = DatagramChannel.open();
        channel.configureBlocking(false);
        channel.bind(local);
        selectionKey = eventLoop.add(channel, SelectionKey.OP_READ, this);
        retransmission.schedule(new TimerTask() {
            @Override
            public void run() {
                eventLoop.queue(() -> {
                    var it = unconfirmed.iterator();
                    while (it.hasNext()) {
                        var s = it.next();
                        if (s.re >= reCount) {
                            it.remove();
                            if (dropCallback != null) {
                                dropCallback.accept(s);
                            }
                        } else {
                            channel.send(s.b.duplicate(), s.a);
                            ++s.re;
                        }
                    }
                });
            }
        }, 0, confirmPeriod);
    }
    private void _write(SocketAddress a, ByteBuffer _buffer) throws Exception {
        var b = ByteBuffer.wrap(_buffer.array());
        b.position(_buffer.position() + _buffer.arrayOffset() - 4);
        b.limit(_buffer.limit() + _buffer.arrayOffset());
        var c = count++;
        b.putInt(c);
        b.position(b.position() - 4);

        var s = new Send(a, b.duplicate(), c);
        if (unsent.size() == 0) {
            channel.send(b, a);
            if (b.remaining() == 0) {
                unconfirmed.addLast(s);
            }
        }
        if (b.remaining() > 0) {
            unsent.addLast(s);
            if (!selectionKey.isWritable()) {
                selectionKey.interestOpsOr(SelectionKey.OP_WRITE);
            }
        }
    }
    public void write(SocketAddress a, ByteBuffer buffer) throws Exception {
        eventLoop.run(() -> _write(a, buffer));
    }
    public void queueWrite(SocketAddress a, ByteBuffer buffer) {
        eventLoop.queue(() -> _write(a, buffer));
    }

    public void forceRemove(SocketAddress a) throws Exception {
        eventLoop.run(() -> handleRemove(a));
    }
    public void queueForceRemove(SocketAddress a) {
        eventLoop.queue(() -> handleRemove(a));
    }

    private void confirm(SocketAddress a, int i) throws IOException {
        ByteBuffer b = ByteBuffer.allocate(4);
        b.putInt(i);
        b.flip();
        channel.send(b, a);
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
            ByteBuffer b = ByteBuffer.allocate(bufferSize);
            var a = channel.receive(b);
            if (a != null) {
                b.flip();
                var i = b.getInt();
                if (b.remaining() > 0) {
                    confirm(a, i);
                    var io = received.computeIfAbsent(a, k -> new I(-1));
                    if (i > io.i) {
                        io.i = i;
                        ByteBuffer res = b.slice();
                        if (readCallback != null) {
                            readCallback.accept(a, res);
                        }
                    }
                } else {
                    handleConfirm(i);
                }
            } else {
                break;
            }
        }
    }
    private void handleWrite() throws Exception {
        while (unsent.size() > 0) {
            var s = unsent.removeFirst();
            var b = s.b.duplicate();
            channel.send(b, s.a);
            if (b.remaining() > 0) {
                unsent.addFirst(s);
                break;
            } else {
                unconfirmed.add(s);
            }
        }
        if (unsent.size() == 0) {
            selectionKey.interestOpsAnd(~SelectionKey.OP_WRITE);
        }
    }
    private void handleConfirm(int i) throws Exception {
        var it = unconfirmed.iterator();
        while (it.hasNext()) {
            var s = it.next();
            if (s.i == i) {
                it.remove();
                if (confirmedCallback != null) {
                    confirmedCallback.accept(s);
                }
                break;
            }
        }
    }
    private void handleRemove(SocketAddress a) {
        received.remove(a);
    }

}
