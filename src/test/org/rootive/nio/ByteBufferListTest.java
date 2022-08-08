package org.rootive.nio;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

@Deprecated
public class ByteBufferListTest {
//    @Test
//    public void basicTest() {
//        ByteBufferList buffers = new ByteBufferList();
//        ByteBuffer buffer1 = ByteBuffer.allocate(16);
//        ByteBuffer buffer2 = ByteBuffer.allocate(32);
//        byte[] b1 = "12345678".getBytes(StandardCharsets.UTF_8);
//        byte[] b2 = "1234567812345678".getBytes(StandardCharsets.UTF_8);
//        buffer1.put(b1);
//        buffer2.put(b2);
//        buffer1.limit(buffer1.position());
//        buffer2.limit(buffer2.position());
//        buffer1.position(0);
//        buffer2.position(0);
//        buffers.addLast(buffer1);
//        Assert.assertEquals(buffers.totalRemaining(), b1.length);
//        buffers.addFirst(buffer2);
//        Assert.assertEquals(buffers.totalRemaining(), b1.length + b2.length);
//        buffers.removeFirst();
//        Assert.assertEquals(buffers.totalRemaining(), b1.length);
//    }
//    @Test
//    public void ioTest() throws IOException {
//        EventLoop eventLoop = new EventLoop();
//        ServerSocketChannel listener = ServerSocketChannel.open();
//        listener.bind(new InetSocketAddress("127.0.0.1", 45555));
//        listener.configureBlocking(false);
//        listener.register(eventLoop.getSelector(), SelectionKey.OP_ACCEPT);
//        ArrayList<SocketChannel> li = new ArrayList<>();
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
//                    ByteBufferList buffers = new ByteBufferList();
//                    System.out.println("read " + buffers.readFrom(c, 8) + " from " + c);
//                    System.out.println("after read: buffers elements count " + buffers.size());
//                    buffers.writeTo(c);
//                    System.out.println("after write: buffers elements count " + buffers.size());
//                }
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        });
//        eventLoop.start();
//    }
}
