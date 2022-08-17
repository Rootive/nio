package org.rootive.nio_rpc;

import org.rootive.log.LogLine;
import org.rootive.log.Logger;
import org.rootive.nio.TCPConnection;
import org.rootive.nio.TCPServer;
import org.rootive.rpc.Collecter;
import org.rootive.rpc.Parser;
import org.rootive.rpc.ServerStub;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public class Server {

    static private class ConnectionContext {
        ServerStub stub;
        Collecter collecter = new Collecter();
        ConnectionContext(ServerStub parent) {
            stub = new ServerStub(parent);
        }
    }

    private final TCPServer tcpServer = new TCPServer();
    private final ServerStub stub = new ServerStub(null);

    public Server() {
        tcpServer.setReadCallback(this::onRead);
        tcpServer.setConnectionCallback(this::onConnection);
    }
    public ServerStub getStub() {
        return stub;
    }
    public void init(InetSocketAddress local) throws Exception {
        init(local, Logger.Level.All, System.out);
    }
    public void init(InetSocketAddress local, Logger.Level level, OutputStream output) throws Exception {
        tcpServer.init(local, level, output);
    }
    public void start() {
        tcpServer.start();
    }
    private void onRead(TCPServer s, TCPConnection c) throws Exception {
        ConnectionContext context = (ConnectionContext) c.getContext();
        var buffers = c.getReadBuffers();
        while (buffers.size() > 0) {
            var state = context.collecter.collect(buffers);
            if (state == Collecter.State.Done) {
                var cs = context.collecter.toString();
                Parser parser = new Parser(cs);
                c.write(ByteBuffer.wrap(context.stub.invoke(parser)));
                context.collecter.clear();
            } else if (state == Collecter.State.Error) {
                var msg = context.collecter.toString();
                context.collecter.clear();
                throw new Exception("collect error: " + msg);
            }
        }
    }
    private void onConnection(TCPServer s, TCPConnection c) {
        if (c.getState() == TCPConnection.State.Connected) {
            c.setContext(new ConnectionContext(stub));
        }
    }
}
