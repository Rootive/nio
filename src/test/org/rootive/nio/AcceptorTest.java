package org.rootive.nio;

import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;

@Deprecated
public class AcceptorTest {
//    static private void onConnection(TcpConnection c) {
//        System.out.println("state: " + c.getState());
//    }
//    static private void onMessage(TcpConnection c) throws IOException {
//        var buffers = c.getReadBuffers();
//        System.out.println("read: " + buffers.totalRemaining());
//        while (buffers.size() > 0) {
//            var buffer = buffers.removeFirst();
//            c.write(buffer);
//        }
//    }
//    static private void onWriteFinished(TcpConnection c) {
//        System.out.println("write finished");
//    }
//
//    @Test
//    public void acceptorTest() {
//        try {
//            EventLoop eventLoop = new EventLoop();
//            ArrayList<TcpConnection> cs = new ArrayList<>();
//            TcpConnection.setConnectionCallback(AcceptorTest::onConnection);
//            TcpConnection.setMessageCallback(AcceptorTest::onMessage);
//            TcpConnection.setWriteFinishedCallback(AcceptorTest::onWriteFinished);
//            TcpConnection.setCancelCallback((TcpConnection _c) -> { cs.clear(); System.out.println("cs: " + cs.size()); });
//            Acceptor acceptor = new Acceptor();
//            acceptor.bind(new InetSocketAddress("127.0.0.1", 45555), (SocketChannel sc) -> {
//                var c = new TcpConnection(sc);
//                cs.add(c);
//                c.register(eventLoop);
//            });
//            eventLoop.init((SelectionKey sk) -> {
//                if (sk.isAcceptable()) { return acceptor; }
//                else { return cs.get(0); }
//            });
//            acceptor.register(eventLoop);
//            eventLoop.start();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
}
