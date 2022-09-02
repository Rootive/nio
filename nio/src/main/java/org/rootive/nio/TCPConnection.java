package org.rootive.nio;

import org.rootive.util.ByteBufferList;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.function.Consumer;

public class TCPConnection implements Handler {
    public enum State {
        Disconnected, Connecting, Connected, Disconnecting
    }

    private static final int bufferElementLength = 1024;
    private static final int hwm = 64 * 1024 * 1024;

    private Consumer<TCPConnection> connectionCallback;
    private Consumer<TCPConnection> readCallback;
    private Consumer<TCPConnection> writeFinishedCallback;
    private Consumer<TCPConnection> hwmCallback;

    private final ByteBufferList readBuffers = new ByteBufferList();
    private final ByteBufferList writeBuffers = new ByteBufferList();
    private final SocketChannel socketChannel;
    private SelectionKey selectionKey;
    private State state = State.Connecting;
    private EventLoop eventLoop;
    public Object context;

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

    public void setConnectionCallback(Consumer<TCPConnection> connectionCallback) {
        this.connectionCallback = connectionCallback;
    }
    public void setReadCallback(Consumer<TCPConnection> readCallback) {
        this.readCallback = readCallback;
    }
    public void setWriteFinishedCallback(Consumer<TCPConnection> writeFinishedCallback) {
        this.writeFinishedCallback = writeFinishedCallback;
    }
    public void setHwmCallback(Consumer<TCPConnection> hwmCallback) {
        this.hwmCallback = hwmCallback;
    }

    public SocketAddress getRemoteSocketAddress() {
        return socketChannel.socket().getRemoteSocketAddress();
    }
    private void handleRead() {
        int res = -1;
        try {
            res = readBuffers.readFrom(socketChannel, bufferElementLength);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (res == -1) {
            handleClose();
        } else if (res > 0 && readCallback != null) {
            readCallback.accept(this);
        }
    }
    private void handleWrite() {
        if (selectionKey.isWritable()) {
            try {
                writeBuffers.writeTo(socketChannel);
            } catch (IOException e) {
                e.printStackTrace();
            }
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
    private void handleClose() {
        state = State.Disconnected;
        selectionKey.interestOpsAnd(0);
        selectionKey.cancel();
        try {
            socketChannel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (connectionCallback != null) {
            connectionCallback.accept(this);
        }
    }
    @Override
    public void handleEvent() {
        if (selectionKey.isReadable()) {
            handleRead();
        } else if (selectionKey.isWritable()) {
            handleWrite();
        }
    }

    private void _disconnect() {
        if (!selectionKey.isWritable()) {
            try {
                socketChannel.shutdownOutput();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    private void _write(ByteBuffer _buffer) {
        ByteBuffer buffer = _buffer.duplicate();
        if (!selectionKey.isWritable() && writeBuffers.totalRemaining() == 0) {
            try {
                socketChannel.write(buffer);
            } catch (IOException e) {
                e.printStackTrace();
            }
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

    public void register(EventLoop eventLoop) {
        try {
            socketChannel.configureBlocking(false);
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.eventLoop = eventLoop;
        try {
            selectionKey = eventLoop.add(socketChannel, SelectionKey.OP_READ, this);
        } catch (ClosedChannelException e) {
            e.printStackTrace();
        }
        state = State.Connected;
        if (connectionCallback != null) {
            connectionCallback.accept(this);
        }
    }
    public void disconnect() {
        if (state == State.Connected) {
            state = State.Disconnecting;
            eventLoop.run(this::_disconnect);
        }
    }
    public void forceDisconnect() {
        if (state == State.Connected || state == State.Disconnecting) {
            state = State.Disconnecting;
            eventLoop.run(this::handleClose);
        }
    }
    public void write(ByteBuffer byteBuffer) {
        if (state == State.Connected) {
            eventLoop.run(() -> _write(byteBuffer));
        }
    }
}
