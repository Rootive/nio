package org.rootive.nio;

import org.rootive.log.LogLine;
import org.rootive.log.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.HashMap;

public class TCPClient {
    private final EventLoopThreadPool threads;
    private InetSocketAddress local;
    private final EventLoop eventLoop;
    private TCPConnection.Callback connectionCallback;
    private TCPConnection.Callback readCallback;

    public TCPClient(EventLoop eventLoop, int threadsCount) {
        threads = new EventLoopThreadPool(threadsCount);
        this.eventLoop = eventLoop;
    }

    public void setThreadInitFunction(EventLoopThread.ThreadInitFunction threadInitFunction) {
        threads.setThreadInitFunction(threadInitFunction);
    }
    public void setConnectionCallback(TCPConnection.Callback connectionCallback) {
        this.connectionCallback = connectionCallback;
    }
    public void setReadCallback(TCPConnection.Callback readCallback) {
        this.readCallback = readCallback;
    }

    public void init(InetSocketAddress local) throws Exception {
        this.local = local;
        threads.start();
    }
    public void open(InetSocketAddress a) throws Exception {
        var s = SocketChannel.open();

        s.bind(local);
        s.configureBlocking(false);
        s.connect(a);
        TCPConnection connection = new TCPConnection(s);
        connection.setConnectionCallback(this::onConnection);
        connection.setReadCallback(this::onRead);
        connection.setWriteFinishedCallback(this::onWriteFinished);
        connection.setHwmCallback(this::onHwm);
        var e = getE();
        e.run(() -> connection.register(e));
    }

    private EventLoop getE() {
        if (threads.count() > 0) {
            return threads.get().getEventLoop();
        } else {
            return eventLoop;
        }
    }
    private void onConnection(TCPConnection connection) throws Exception {
        LogLine.begin(Logger.Level.Info).log(connection.toString() + ": " + connection.getState()).end();
        if (connectionCallback != null) {
            connectionCallback.accept(connection);
        }
    }
    private void onRead(TCPConnection connection) throws Exception {
        if (readCallback != null) {
            readCallback.accept(connection);
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
