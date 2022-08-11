package org.rootive.nio;

import org.rootive.log.LogLine;
import org.rootive.log.Logger;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

public class TCPServer {
    @FunctionalInterface
    interface Callback {
        void accept(TCPServer s, TCPConnection c) throws Exception;
    }

    private final int threadsCount = 4;
    private final EventLoopThreadPool threads = new EventLoopThreadPool(threadsCount);
    private final EventLoop eventLoop = new EventLoop();
    private final Acceptor acceptor = new Acceptor();
    private Callback connectionCallback;
    private Callback readCallback;

    public TCPServer() {
        TCPConnection.setConnectionCallback(this::onConnection);
        TCPConnection.setReadCallback(this::onRead);
        TCPConnection.setWriteFinishedCallback(this::onWriteFinished);
        TCPConnection.setHwmCallback(this::onHwm);
    }

    public void setThreadInitFunction(EventLoopThread.ThreadInitFunction threadInitFunction) {
        threads.setThreadInitFunction(threadInitFunction);
    }
    public void setConnectionCallback(Callback connectionCallback) {
        this.connectionCallback = connectionCallback;
    }
    public void setReadFinishedCallback(Callback readFinishedCallback) {
        this.readCallback = readFinishedCallback;
    }

    public void init(InetSocketAddress local) throws Exception {
        Logger.start(Logger.Level.All, System.out);
        eventLoop.init();
        acceptor.bind(local, this::onNewConnection);
        acceptor.register(eventLoop);
        threads.start();
    }
    public void init(InetSocketAddress local, Logger.Level level, OutputStream output) throws Exception {
        Logger.start(level, output);
        eventLoop.init();
        acceptor.bind(local, this::onNewConnection);
        acceptor.register(eventLoop);
        threads.start();
    }
    public void start() {
        eventLoop.start();
    }

    private void onNewConnection(SocketChannel sc) {
        var e = threads.get().getEventLoop();
        e.queue(() -> new TCPConnection(sc).register(e));
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
