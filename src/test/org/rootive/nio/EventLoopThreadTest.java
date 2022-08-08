package org.rootive.nio;

import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Scanner;

@Deprecated
public class EventLoopThreadTest {
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
//    public void eventLoopThreadTest() {
//        try {
//            EventLoopThread th = new EventLoopThread();
//            Acceptor acceptor = new Acceptor();
//            ArrayList<TcpConnection> cs = new ArrayList<>();
//
//            TcpConnection.setConnectionCallback(EventLoopThreadTest::onConnection);
//            TcpConnection.setMessageCallback(EventLoopThreadTest::onMessage);
//            TcpConnection.setWriteFinishedCallback(EventLoopThreadTest::onWriteFinished);
//            TcpConnection.setCancelCallback((TcpConnection _c) -> { cs.clear(); System.out.println("cs: " + cs.size()); });
//
//            th.init((SelectionKey sk) -> {
//                if (sk.isAcceptable()) { return acceptor; }
//                else { return cs.get(0); }
//            });
//            acceptor.bind(new InetSocketAddress("127.0.0.1", 45555), (SocketChannel sc) -> {
//                var c = new TcpConnection(sc);
//                cs.add(c);
//                c.register(th.getEventLoop());
//            });
//
//            th.start();
//            th.getEventLoop().run(new Runnable() {
//                @Override
//                public void run() {
//                    try {
//                        acceptor.register(th.getEventLoop());
//                    } catch (ClosedChannelException e) {
//                        e.printStackTrace();
//                    }
//                }
//            });
//            System.out.println("start");
//            Scanner in = new Scanner(System.in);
//            in.nextLine();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
}
