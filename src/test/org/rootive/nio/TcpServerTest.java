package org.rootive.nio;

import org.junit.Test;

import java.io.Writer;
import java.net.InetSocketAddress;

public class TcpServerTest {
    static private void onConnection(TcpConnection c) {
        System.out.println(Thread.currentThread().getId() + " state: " + c.getState());
    }
    static private void onMessage(TcpConnection c) throws Exception {
        var buffers = c.getReadBuffers();
        System.out.println(Thread.currentThread().getId() + " read: " + buffers.totalRemaining());
        while (buffers.size() > 0) {
            var buffer = buffers.removeFirst();
            c.write(buffer);
        }
    }
    static private void onWriteFinished(TcpConnection c) {
        System.out.println(Thread.currentThread().getId() + " write finished");
    }
    @Test
    public void tcpServerTest() {
        TcpConnection.setConnectionCallback(TcpServerTest::onConnection);
        TcpConnection.setMessageCallback(TcpServerTest::onMessage);
        TcpConnection.setWriteFinishedCallback(TcpServerTest::onWriteFinished);

        TcpServer server = new TcpServer();
        try {
            server.init(new InetSocketAddress("127.0.0.1", 45555));
            server.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
