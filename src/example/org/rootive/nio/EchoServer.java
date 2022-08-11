package org.rootive.nio;

import org.rootive.log.LogLine;
import org.rootive.log.Logger;

import java.net.InetSocketAddress;

public class EchoServer {
    static private void onRead(TCPServer s, TCPConnection c) throws Exception {
        var buffers = c.getReadBuffers();
        LogLine.begin(Logger.Level.Trace).log(c + ": read " + buffers.totalRemaining()).end();
        while (buffers.size() > 0) {
            var buffer = buffers.removeFirst();
            c.write(buffer);
        }
    }
    public static void main(String[] args) throws Exception {
        TCPServer server = new TCPServer();
        server.setReadFinishedCallback(EchoServer::onRead);
        server.init(new InetSocketAddress(45555));
        server.start();
    }
}
