package org.rootive.nio;

import org.rootive.gadget.ByteBufferList;

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

public class TCPConnection {

    public enum State {
        Disconnected, Connecting, Connected, Disconnecting
    }
    @FunctionalInterface
    public interface Callback {
        void accept(TCPConnection c) throws Exception;
    }

    private static final int bufferElementLength = 1024;
    private static final int hwm = 64 * 1024 * 1024;

    private Callback connectionCallback;
    private Callback readCallback;
    private Callback writeFinishedCallback;
    private Callback hwmCallback;

    private final SocketChannel socketChannel;
    private final ByteBufferList readBuffers = new ByteBufferList();
    private final ByteBufferList writeBuffers = new ByteBufferList();
    private SelectionKey selectionKey;
    private State state = State.Connecting;
    private EventLoop eventLoop;
    private Object context;

    public TCPConnection(SocketChannel socketChannel){
        this.socketChannel = socketChannel;
    }

    public ByteBufferList getReadBuffers() {
        return readBuffers;
    }
    public State getState() {
        return state;
    }
    public int getWriteBuffersRemaining() { return writeBuffers.totalRemaining(); }
    public Object getContext() {
        return context;
    }
    public void setContext(Object context) {
        this.context = context;
    }
    public void setConnectionCallback(Callback connectionCallback) {
        this.connectionCallback = connectionCallback;
    }
    public void setReadCallback(Callback readCallback) {
        this.readCallback = readCallback;
    }
    public void setWriteFinishedCallback(Callback writeFinishedCallback) {
        this.writeFinishedCallback = writeFinishedCallback;
    }
    public void setHwmCallback(Callback hwmCallback) {
        this.hwmCallback = hwmCallback;
    }

    @Override
    public String toString() {
        return socketChannel.socket().getInetAddress().toString()+
                '@' + Integer.toHexString(hashCode());
    }
    private void handleRead() throws Exception {
        int res;
        try {
            res = readBuffers.readFrom(socketChannel, bufferElementLength);
        } catch (Exception e) {
            forceDisconnect();
            throw e;
        }
        if (res == -1) {
            handleClose();
        } else if (res > 0 && readCallback != null) {
            readCallback.accept(this);
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
        socketChannel.close();
        if (connectionCallback != null) {
            connectionCallback.accept(this);
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
            writeBuffers.addLast(buffer);
            if (!selectionKey.isWritable()) {
                selectionKey.interestOpsOr(SelectionKey.OP_WRITE);
            }
            if (writeBuffers.totalRemaining() >= hwm && hwmCallback != null) {
                hwmCallback.accept(_this);
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
