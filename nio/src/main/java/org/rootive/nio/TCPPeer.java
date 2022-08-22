package org.rootive.nio;

import java.net.InetSocketAddress;

public class TCPPeer {
    class Context {
        public String name;

        public Context(String name) {
            this.name = name;
        }
    }

    private final EventLoop eventLoop;
    private final TCPServer server;
    private final TCPClient client;
    private final TCPConnectionPool connections = new TCPConnectionPool();

    private TCPConnection.Callback connectionCallback;
    private TCPConnection.Callback readCallback;

    public TCPPeer(EventLoop eventLoop) {
        this.eventLoop = eventLoop;
        server = new TCPServer(eventLoop, 0);
        client = new TCPClient(eventLoop, 0);
    }

    public void setConnectionCallback(TCPConnection.Callback connectionCallback) {
        this.connectionCallback = connectionCallback;
    }
    public void setReadCallback(TCPConnection.Callback readCallback) {
        this.readCallback = readCallback;
    }

    public void init(InetSocketAddress listen, InetSocketAddress local) throws Exception {
        server.setConnectionCallback(this::onConnection);
        server.setReadCallback(this::onRead);
        server.init(listen);
        client.setConnectionCallback(this::onConnection);
        client.setReadCallback(this::onRead);
        client.init(local);
    }
    public void open(InetSocketAddress a) throws Exception {
        client.open(a);
    }

    private void onConnection(TCPConnection connection) throws Exception {
        var state = connection.getState();
        switch (state) {
            case Connected -> {
                var as = connection.getRemoteSocketAddress().toString();
                connection.setContext(new Context(as));
                connections.put(as, connection);
            }
            case Disconnected -> {
                var as = ((Context) connection.getContext()).name;
                connections.remove(as);
            }
        }
        if (connectionCallback != null) {
            connectionCallback.accept(connection);
        }
    }
    private void onRead(TCPConnection connection) throws Exception {
        if (readCallback != null) {
            readCallback.accept(connection);
        }
    }
}
