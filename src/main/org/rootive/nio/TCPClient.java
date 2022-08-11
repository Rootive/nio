package org.rootive.nio;

import org.rootive.log.LogLine;
import org.rootive.log.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class TCPClient {
    @FunctionalInterface
    interface Callback {
        void accept(TCPClient client, TCPConnection c) throws Exception;
    }

    private TCPConnection connection;
    private final EventLoop eventLoop = new EventLoop();
    private Callback connectionCallback;
    private Callback readCallback;

    public TCPClient() {
        TCPConnection.setConnectionCallback(this::onConnection);
        TCPConnection.setReadCallback(this::onRead);
        TCPConnection.setWriteFinishedCallback(this::onWriteFinished);
        TCPConnection.setHwmCallback(this::onHwm);
    }
    public void setConnectionCallback(Callback connectionCallback) {
        this.connectionCallback = connectionCallback;
    }
    public void setReadCallback(Callback readCallback) {
        this.readCallback = readCallback;
    }
    public void init() throws IOException, InterruptedException {
        Logger.start(Logger.Level.All, System.out);
        eventLoop.init();
    }
    public void init(Logger.Level level, OutputStream output) throws IOException, InterruptedException {
        Logger.start(level, output);
        eventLoop.init();
    }
    public void open(InetSocketAddress address) throws Exception {
        connection = new TCPConnection(SocketChannel.open(address));
        connection.register(eventLoop);
    }
    public void start() {
        eventLoop.start();
    }
    public void queueWrite(ByteBuffer buffer) {
        connection.queueWrite(buffer);
    }
    public void queueDisconnect() {
        connection.queueDisconnect();
    }
    private void onConnection(TCPConnection connection) throws Exception {
        LogLine.begin(Logger.Level.Info).log(connection.toString() + ": " + connection.getState()).end();
        if (connectionCallback != null) {
            connectionCallback.accept(this, connection);
        }
    }
    private void onRead(TCPConnection connection) throws Exception {
        if (readCallback != null) {
            readCallback.accept(this, connection);
        }
    }
    private void onWriteFinished(TCPConnection connection) {
        LogLine.begin(Logger.Level.Info).log(connection.toString() + ": write finished").end();
    }
    private void onHwm(TCPConnection connection) throws Exception {
        LogLine.begin(Logger.Level.Info).log(connection.toString() + ": high-water mark " + connection.getWriteBuffersRemaining()).end();
        connection.forceDisconnect();
    }
}
