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
    public interface Callback {
        void accept(TCPClient client, TCPConnection c) throws Exception;
    }

    private TCPConnection connection;
    private final EventLoop eventLoop = new EventLoop();
    private Callback connectionCallback;
    private Callback readCallback;

    public void setConnectionCallback(Callback connectionCallback) {
        this.connectionCallback = connectionCallback;
    }
    public void setReadCallback(Callback readCallback) {
        this.readCallback = readCallback;
    }
    public void init() throws IOException, InterruptedException {
        init(Logger.Level.All, System.out);
    }
    public void init(Logger.Level level, OutputStream output) throws IOException, InterruptedException {
        Logger.start(level, output);
        eventLoop.init();
    }
    public void open(InetSocketAddress address) throws Exception {
        connection = new TCPConnection(SocketChannel.open(address));
        connection.setConnectionCallback(this::onConnection);
        connection.setReadCallback(this::onRead);
        connection.setWriteFinishedCallback(this::onWriteFinished);
        connection.setHwmCallback(this::onHwm);
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
