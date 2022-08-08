package org.rootive.nio;

import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;

@Deprecated
public class EventLoopTest {
//    @Test
//    public void eventLoopTest() throws IOException {
//        EventLoop eventLoop = new EventLoop();
//        ServerSocketChannel listener = ServerSocketChannel.open();
//        listener.bind(new InetSocketAddress("127.0.0.1", 45555));
//        listener.configureBlocking(false);
//        listener.register(eventLoop.getSelector(), SelectionKey.OP_ACCEPT);
//        ArrayList<SocketChannel> li = new ArrayList<>(); // 不得不承认，我的Java学的不好：如果不维护这个ArrayList，accept的SocketChannel会被销毁吗？
//        eventLoop.setHandle((SelectionKey selectionKey) -> {
//            try {
//                var ready = selectionKey.readyOps();
//                if ((ready & SelectionKey.OP_ACCEPT) > 0) {
//                    var s = (ServerSocketChannel) (selectionKey.channel());
//                    var c = s.accept();
//                    System.out.println("accept " + c);
//                    li.add(c);
//                    c.configureBlocking(false);
//                    c.register(eventLoop.getSelector(), SelectionKey.OP_READ);
//                } else if ((ready & SelectionKey.OP_READ) > 0) {
//                    var c = (SocketChannel) (selectionKey.channel());
//                    ByteBuffer buffer = ByteBuffer.allocate(1024);
//                    System.out.println("read " + c.read(buffer) + " from " + c);
//                    buffer.limit(buffer.position());
//                    buffer.position(0);
//                    c.write(buffer);
//                }
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        });
//        var th = new Thread(new Runnable() {
//            @Override
//            public void run() {
//                System.out.println("th " + Thread.currentThread().getId());
//                while (true) {
//                    eventLoop.run(new Runnable() {
//                        @Override
//                        public void run() {
//                            System.out.println("run in " + Thread.currentThread().getId());
//                        }
//                    });
//                    try {
//                        Thread.sleep(3000);
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
//                }
//            }
//        });
//        th.start();
//        eventLoop.start();
//    }
}
