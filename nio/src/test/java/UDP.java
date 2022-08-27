import org.junit.Test;
import org.rootive.log.Logger;
import org.rootive.nio.EventLoopThread;
import org.rootive.nio.RUDPConnection;
import org.rootive.nio.RUDPServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.Scanner;
import java.util.Timer;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public class UDP {
    static String bufferToString(ByteBuffer b) {
        return new String(b.array(), b.position() + b.arrayOffset(), b.remaining());
    }

    RUDPServer xs;
    RUDPServer ys;
    @Test
    public void x() throws Exception {
        Logger.start(Logger.Level.All, System.out);
        EventLoopThread t = new EventLoopThread();
        t.setThreadInitFunction((e) -> {
            xs = new RUDPServer(new ScheduledThreadPoolExecutor(1), e, 2);
            xs.setReadCallback((c, l) -> {
                var d = l.head();
                do {
                    System.out.println(c + ": " + bufferToString(d.v));
                    d = d.right();
                } while (d != null);
            });
            xs.setStateCallback((c) -> {
                if (c.getState() == RUDPConnection.State.Connected) {
                    Thread th = new Thread(() -> {
                        try {
                            ByteBuffer b = RUDPConnection.newByteBuffer();
                            b.put("hello".getBytes());
                            b.flip();
                            c.message(b);

                            ByteBuffer d = RUDPConnection.newByteBuffer();
                            d.put(", ".getBytes());
                            d.flip();
                            c.message(d);
                            c.flush();

                            ByteBuffer f = RUDPConnection.newByteBuffer();
                            f.put("world".getBytes());
                            f.flip();
                            c.message(f);

                            ByteBuffer g = RUDPConnection.newByteBuffer();
                            g.put("! ".getBytes());
                            g.flip();
                            c.message(g);

                        } catch (Exception ep) {
                            ep.printStackTrace();
                        }
                    });
                    th.start();
                }
            });
            xs.init(new InetSocketAddress(45555));
        });
        t.start();
        t.join();
    }

    @Test
    public void y() throws Exception {
        Logger.start(Logger.Level.All, System.out);
        EventLoopThread t = new EventLoopThread();

        t.setThreadInitFunction((e) -> {
            ys = new RUDPServer(new ScheduledThreadPoolExecutor(1), e, 2);
            ys.setReadCallback((c, l) -> {
                var d = l.head();
                do {
                    System.out.println(c + ": " + bufferToString(d.v));
                    d = d.right();
                } while (d != null);
            });
            ys.init(new InetSocketAddress(45556));
        });
        t.start();
        t.getEventLoop().run(() -> ys.get(new InetSocketAddress("127.0.0.1", 45555)));
        t.join();
    }

    public static void main(String[] args) throws IOException {
        var local = new InetSocketAddress(45557);
        var remote = new InetSocketAddress("127.0.0.1", 45555);
        DatagramChannel c = DatagramChannel.open();
        c.bind(local);
        c.connect(remote);
        new Thread(() -> {
            try {
                ByteBuffer b = ByteBuffer.allocate(RUDPConnection.MTU);
                while (true) {
                    b.clear();
                    c.read(b);
                    b.flip();
                    if (b.remaining() <= 0) {
                        continue;
                    }
                    String s = "read: ";
                    s += b.getInt();
                    if (b.remaining() > 0) {
                        s += " ";
                        s += b.getInt();
                    }
                    if (b.remaining() > 0) {
                        s += " ";
                        s += bufferToString(b);
                    }
                    System.out.println(s);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
        ByteBuffer b = ByteBuffer.allocate(RUDPConnection.MTU);
        Scanner in = new Scanner(System.in);
        while (true) {
            b.clear();
            b.putInt(2);
            b.putInt(in.nextInt());
            var s = in.next();
            if (!s.equals(";")) {
                b.put(s.getBytes());
            }
            b.flip();
            c.write(b);
        }
    }

}
