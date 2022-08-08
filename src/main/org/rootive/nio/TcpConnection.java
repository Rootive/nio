package org.rootive.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

//Rootive: TcpConnection是对SocketChannel的封装，保存了与连接相关的东西，如读写缓冲区。
//现在有一个难题：Selector返回的是SelectableChannel，我们需要从这个SelectableChannel得到封装它的TcpConnection。
//一个直观的方法都是用Map维护。但在我的设想中应该用多态实现：实现一个继承于SocketChannel的TcpConnection2，
//即accept的时候用SocketChannel构造一个TcpConnection2，直接用新构造的TcpConnection2调用register。
//这样做的好处就是不需要维护Map。
//但SocketChannel是一个虚类，他的实现SocketChannelImpl是非public的。
//所以，有没有更好的方法？
//possible answer From https://stackoverflow.com/questions/7022628/extend-socketchannel-so-that-selectionkey-returns-custom-class:
// If you need context with your SocketChannel, that's what the attachment is for. Basic answer is 'no'.

public class TcpConnection{

    enum State {
        Disconnected, Connecting, Connected, Disconnecting
    }
    @FunctionalInterface
    interface Callback {
        void accept(TcpConnection c) throws Exception;
    }

    private static final int bufferElementLength = 1024;
    private static final int hwm = 64 * 1024 * 1024;

    static private Callback connectionCallback;
    static private Callback messageCallback;
    static private Callback writeFinishedCallback;
    static private Callback hwmCallback;
    static private Callback cancelCallback;

    private final SocketChannel socketChannel;
    private final ByteBufferList readBuffers = new ByteBufferList();
    private final ByteBufferList writeBuffers = new ByteBufferList();
    private SelectionKey selectionKey;
    private State state = State.Connecting;;
    private EventLoop eventLoop;
    public Object context;

    public TcpConnection(SocketChannel socketChannel){
        this.socketChannel = socketChannel;
    }

    public ByteBufferList getReadBuffers() {
        return readBuffers;
    }
    public State getState() {
        return state;
    }
    public int getWriteBuffersRemaining() { return writeBuffers.totalRemaining(); }

    static public void setConnectionCallback(Callback connectionCallback) {
        TcpConnection.connectionCallback = connectionCallback;
    }
    static public void setMessageCallback(Callback messageCallback) {
        TcpConnection.messageCallback = messageCallback;
    }
    static public void setWriteFinishedCallback(Callback writeFinishedCallback) {
        TcpConnection.writeFinishedCallback = writeFinishedCallback;
    }
    static public void setHwmCallback(Callback hwmCallback) {
        TcpConnection.hwmCallback = hwmCallback;
    }
    static public void setCancelCallback(Callback cancelCallback) {
        TcpConnection.cancelCallback = cancelCallback;
    }

    private void handleRead() throws Exception {
        int res = 0;
        try {
            res = readBuffers.readFrom(socketChannel, bufferElementLength);
        } catch (Exception e) {
            forceDisconnect();
            throw e;
        }
        if (res == -1) {
            handleClose();
        } else if (res > 0 && messageCallback != null) {
            messageCallback.accept(this);
        }
    }
    private void handleWrite() throws Exception {
        if (selectionKey.isWritable()) {
            writeBuffers.writeTo(socketChannel);
            if (writeBuffers.totalRemaining() == 0) {
                selectionKey.interestOpsAnd(~SelectionKey.OP_WRITE);
                if (writeFinishedCallback != null) {
                    writeFinishedCallback.accept(this);
                }
                if (state == State.Disconnecting) {
                    _disconnect();
                }
            }
        }
    }
    private void handleClose() throws Exception {
        state = State.Disconnected;
        selectionKey.interestOpsAnd(0);
        selectionKey.cancel();
        eventLoop.remove(selectionKey);
        if (connectionCallback != null) {
            connectionCallback.accept(this);
        }
        if (cancelCallback != null) {
            cancelCallback.accept(this);
        }
    }
    public void handleEvent() throws Exception {
        var ready = selectionKey.readyOps();
        if ((ready & SelectionKey.OP_READ) > 0) {
            handleRead();
        } else if ((ready & SelectionKey.OP_WRITE) > 0) {
            handleWrite();
        }
    }

    private void _disconnect() throws IOException {
        if (!selectionKey.isWritable()) {
            socketChannel.shutdownOutput();
        }
    }
    private void _write(ByteBuffer _buffer) throws Exception {
        ByteBuffer buffer = _buffer.duplicate();
        var _this = this;
        if (!selectionKey.isWritable() && writeBuffers.totalRemaining() == 0) {
            socketChannel.write(buffer);
            if (buffer.remaining() == 0 && writeFinishedCallback != null) {
                writeFinishedCallback.accept(_this);
            }
        }
        if (buffer.remaining() > 0) {
            var old = writeBuffers.totalRemaining();
            var current = old + buffer.remaining();
            if (current >= hwm && old < hwm && hwmCallback != null) {
                hwmCallback.accept(_this);
            }
            writeBuffers.addLast(buffer);
            if (!selectionKey.isWritable()) {
                selectionKey.interestOpsOr(SelectionKey.OP_WRITE);
            }
        }
    }

    public void register(EventLoop eventLoop) throws Exception {
        socketChannel.configureBlocking(false);
        selectionKey = eventLoop.add(socketChannel, (sk) -> handleEvent(), SelectionKey.OP_READ);
        this.eventLoop = eventLoop;
        state = State.Connected;
        if (connectionCallback != null) {
            connectionCallback.accept(this);
        }
    }
    public void disconnect() throws IOException {
        if (state == State.Connected) {
            state = State.Disconnecting;
            if (eventLoop.isThread()) {
                _disconnect();
            } else {
                eventLoop.queue(this::_disconnect);
            }
        }
    }
    public void forceDisconnect() throws Exception {
        if (state == State.Connected || state == State.Disconnecting) {
            state = State.Disconnecting;
            if (eventLoop.isThread()) {
                handleClose();
            } else {
                eventLoop.queue(this::handleClose);
            }
        }
    }
    public void write(ByteBuffer byteBuffer) throws Exception {
        if (state == State.Connected) {
            if (eventLoop.isThread()) {
                _write(byteBuffer);
            } else {
                eventLoop.queue(() -> _write(byteBuffer));

            }
        }
    }
    public void queueDisconnect() {
        if (state == State.Connected) {
            state = State.Disconnecting;
            eventLoop.queue(this::_disconnect);
        }
    }
    public void queueForceDisconnect() {
        if (state == State.Connected || state == State.Disconnecting) {
            state = State.Disconnecting;
            eventLoop.queue(this::handleClose);
        }
    }
    public void queueWrite(ByteBuffer byteBuffer) {
        if (state == State.Connected) {
            eventLoop.queue(() -> _write(byteBuffer));
        }
    }
}
