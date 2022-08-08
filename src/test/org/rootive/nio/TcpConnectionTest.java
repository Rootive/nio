package org.rootive.nio;

import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.util.ArrayList;

@Deprecated
public class TcpConnectionTest {
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
//    @Test
//    public void tcpConnectionTest() {
//        try {
//            EventLoop eventLoop = new EventLoop();
//            ServerSocketChannel listener = ServerSocketChannel.open();
//            listener.bind(new InetSocketAddress("127.0.0.1", 45555));
//            listener.configureBlocking(false);
//            listener.register(eventLoop.getSelector(), SelectionKey.OP_ACCEPT);
//            ArrayList<TcpConnection> cs = new ArrayList<>();
//            eventLoop.setHandle((SelectionKey selectionKey) -> {
//                try {
//                    var ready = selectionKey.readyOps();
//                    if ((ready & SelectionKey.OP_ACCEPT) > 0) {
//                        var s = (ServerSocketChannel) (selectionKey.channel());
//                        var c = new TcpConnection(s.accept());
//                        c.setConnectionCallback(TcpConnectionTest::onConnection);
//                        c.setMessageCallback(TcpConnectionTest::onMessage);
//                        c.setWriteFinishedCallback(TcpConnectionTest::onWriteFinished);
//                        c.setCancelCallback((TcpConnection _c) -> { cs.clear(); System.out.println("cs: " + cs.size()); });
//                        cs.add(c);
//                        c.register(eventLoop);
//                    } else {
//                        cs.get(0).handleEvent();
//                    }
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//            });
//            eventLoop.start();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
}
