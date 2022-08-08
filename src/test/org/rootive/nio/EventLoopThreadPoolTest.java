package org.rootive.nio;

import org.junit.Test;

import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
@Deprecated
public class EventLoopThreadPoolTest {
//    static private void onConnection(TcpConnection c) {
//        System.out.println(Thread.currentThread().getId() + " state: " + c.getState());
//    }
//    static private void onMessage(TcpConnection c) throws Exception {
//        var buffers = c.getReadBuffers();
//        System.out.println(Thread.currentThread().getId() + " read: " + buffers.totalRemaining());
//        while (buffers.size() > 0) {
//            var buffer = buffers.removeFirst();
//            c.write(buffer);
//        }
//    }
//    static private void onWriteFinished(TcpConnection c) {
//        System.out.println(Thread.currentThread().getId() + " write finished");
//    }
//
//    @Test
//    public void eventLoopThreadPoolTest() {
//        System.out.println(Thread.currentThread().getId());
//        try {
//            EventLoopThreadPool th = new EventLoopThreadPool(4);
//            EventLoop ath = new EventLoop();
//            Acceptor acceptor = new Acceptor();
//            ArrayList<TcpConnection> cs = new ArrayList<>();
//
//            TcpConnection.setConnectionCallback(EventLoopThreadPoolTest::onConnection);
//            TcpConnection.setMessageCallback(EventLoopThreadPoolTest::onMessage);
//            TcpConnection.setWriteFinishedCallback(EventLoopThreadPoolTest::onWriteFinished);
//            TcpConnection.setCancelCallback((TcpConnection _c) -> {
//                ath.queue(() -> {
//                    cs.clear();
//                    System.out.println(Thread.currentThread().getId() + " cs: " + cs.size());
//                });
//            });
//
//            ath.init();
//            acceptor.bind(new InetSocketAddress("127.0.0.1", 45555), (SocketChannel sc) -> {
//                System.out.println(Thread.currentThread().getId() + " bind");
//                var c = new TcpConnection(sc);
//                cs.add(c);
//                var e = th.get().getEventLoop();
//                e.queue(() -> c.register(e));
//            });
//            acceptor.register(ath);
//            th.start();
//            ath.start();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
}
