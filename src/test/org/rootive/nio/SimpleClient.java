package org.rootive.nio;

import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class SimpleClient {
    public static void main(String[] args) {
        try {
            SocketChannel c = SocketChannel.open(new InetSocketAddress("127.0.0.1", 45555));
            System.out.println("connected");
            c.configureBlocking(false);
            Thread th = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        ByteBuffer buffer = ByteBuffer.allocate(1024);
                        while (true) {
                            buffer.clear();
                            var size = c.read(buffer);
                            if (size > 0) {
                                System.out.println("server: " + new String(buffer.array(), 0, size));
                            }
                        }
                    } catch (Exception e) {

                    }
                }
            });
            th.start();
            while (true) {
                Scanner in = new Scanner(System.in);
                byte[] b = in.nextLine().getBytes(StandardCharsets.UTF_8);
                ByteBuffer buffer = ByteBuffer.allocate(b.length );
                buffer.put(b);
                buffer.limit(buffer.position());
                buffer.position(0);
                System.out.println("write"  + c.write(buffer));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
