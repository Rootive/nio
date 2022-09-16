package org.rootive.nio;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.function.Consumer;

public class TCPServer {
    private final EventLoopThreadPool threads;
    private final EventLoop eventLoop;
    private final Acceptor acceptor = new Acceptor();
    private Consumer<TCPConnection> connectionCallback;
    private Consumer<TCPConnection> readCallback;

    public TCPServer(EventLoop eventLoop, int threadsCount) {
        threads = new EventLoopThreadPool(threadsCount);
        this.eventLoop = eventLoop;
    }

    public void setThreadInitFunction(Consumer<EventLoop> threadInitFunction) {
        threads.setThreadInitFunction(threadInitFunction);
    }
    public void setConnectionCallback(Consumer<TCPConnection> connectionCallback) {
        this.connectionCallback = connectionCallback;
    }
    public void setReadCallback(Consumer<TCPConnection> readCallback) {
        this.readCallback = readCallback;
    }

    public void init(InetSocketAddress local) throws Exception {
        acceptor.bind(local, this::onNewConnection);
        acceptor.register(eventLoop);
        threads.start();
    }

    private EventLoop getE() {
        if (threads.count() > 0) {
            return threads.get().getEventLoop();
        } else {
            return eventLoop;
        }
    }
    private void onNewConnection(SocketChannel sc) {
        var e = getE();
        var connection = new TCPConnection(sc);
        connection.setConnectionCallback(this::onConnection);
        connection.setReadCallback(this::onRead);
        connection.setWriteFinishedCallback(this::onWriteFinished);
        connection.setHwmCallback(this::onHwm);
        e.run(() -> {
            try {
                connection.register(e);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });
    }
    private void onConnection(TCPConnection connection) {
        System.out.println(connection.toString() + ": " + connection.getState());
        if (connectionCallback != null) {
            connectionCallback.accept(connection);
        }
    }
    private void onRead(TCPConnection connection) {
        if (readCallback != null) {
            readCallback.accept(connection);
        }
    }
    private void onWriteFinished(TCPConnection connection) {
        System.out.println(connection.toString() + ": write finished");
    }
    private void onHwm(TCPConnection connection) {
        System.out.println(connection.toString() + ": high-water mark " + connection.getWriteBuffersRemaining());
        connection.forceDisconnect();
    }
}
