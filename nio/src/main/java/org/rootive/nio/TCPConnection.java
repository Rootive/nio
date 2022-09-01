package org.rootive.nio;

import org.rootive.gadgets.ByteBufferList;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class TCPConnection implements Handler {

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

    private final ByteBufferList readBuffers = new ByteBufferList();
    private final ByteBufferList writeBuffers = new ByteBufferList();
    private final SocketChannel socketChannel;
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
    public SocketAddress getRemoteSocketAddress() {
        return socketChannel.socket().getRemoteSocketAddress();
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
        socketChannel.close();
        if (connectionCallback != null) {
            connectionCallback.accept(this);
        }
    }
    public void handleEvent() throws Exception {
        if (selectionKey.isReadable()) {
            handleRead();
        } else if (selectionKey.isWritable()) {
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
        if (!selectionKey.isWritable() && writeBuffers.totalRemaining() == 0) {
            socketChannel.write(buffer);
            if (buffer.remaining() == 0 && writeFinishedCallback != null) {
                writeFinishedCallback.accept(this);
            }
        }
        if (buffer.remaining() > 0) {
            writeBuffers.addLast(buffer);
            if (!selectionKey.isWritable()) {
                selectionKey.interestOpsOr(SelectionKey.OP_WRITE);
            }
            if (writeBuffers.totalRemaining() >= hwm && hwmCallback != null) {
                hwmCallback.accept(this);
            }
        }
    }

    public void register(EventLoop eventLoop) throws Exception {
        socketChannel.configureBlocking(false);
        this.eventLoop = eventLoop;
        selectionKey = eventLoop.add(socketChannel, SelectionKey.OP_READ, this);
        state = State.Connected;
        if (connectionCallback != null) {
            connectionCallback.accept(this);
        }
    }
    public void disconnect() throws Exception {
        if (state == State.Connected) {
            state = State.Disconnecting;
            eventLoop.run(this::_disconnect);
        }
    }
    public void forceDisconnect() throws Exception {
        if (state == State.Connected || state == State.Disconnecting) {
            state = State.Disconnecting;
            eventLoop.run(this::handleClose);
        }
    }
    public void write(ByteBuffer byteBuffer) throws Exception {
        if (state == State.Connected) {
            eventLoop.run(() -> _write(byteBuffer));
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
