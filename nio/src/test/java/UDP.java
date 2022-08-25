import org.junit.Test;
import org.rootive.log.Logger;
import org.rootive.nio.EventLoopThread;
import org.rootive.nio.RUDPConnection;
import org.rootive.nio.RUDPServer;
import org.rootive.nio.ReUDPServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.Timer;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class UDP {
    ReUDPServer as;
    ReUDPServer bs;

    static String bufferToString(ByteBuffer b) {
        return new String(b.array(), b.position() + b.arrayOffset(), b.remaining());
    }
    void onRead(SocketAddress a, ByteBuffer b) {
        System.out.println(a + ": " + bufferToString(b));
    }
    void onDrop(ReUDPServer.Send s) {
        var i = s.b.getInt();
        System.out.println("drop: " + i + " " + bufferToString(s.b));
    }
    void onConfirm(ReUDPServer.Send s) {
        var i = s.b.getInt();
        System.out.println("confirm: " + i + " " + bufferToString(s.b));
    }

    @Test
    public void a() throws Exception {
        EventLoopThread t = new EventLoopThread();
        t.setThreadInitFunction((e) -> {
            as = new ReUDPServer(e, new Timer());
            as.setConfirmedCallback(this::onConfirm);
            as.setDropCallback(this::onDrop);
            as.setReadCallback(this::onRead);
            as.init(new InetSocketAddress(45555));
        });
        t.start();

        ByteBuffer b = ReUDPServer.newBuffer();
        b.put("hello".getBytes());
        b.flip();
        as.write(new InetSocketAddress("127.0.0.1", 45556), b.duplicate());
        as.write(new InetSocketAddress("127.0.0.1", 45556), b.duplicate());

        t.join();
    }

    @Test
    public void b() throws Exception {
        EventLoopThread t = new EventLoopThread();
        t.setThreadInitFunction((e) -> {
            bs = new ReUDPServer(e, new Timer());
            bs.setConfirmedCallback(this::onConfirm);
            bs.setDropCallback(this::onDrop);
            bs.setReadCallback(this::onRead);
            bs.init(new InetSocketAddress(45556));
        });
        t.start();
        t.join();
    }

    RUDPServer xs;
    RUDPServer ys;
    @Test
    public void x() throws Exception {
        Logger.start(Logger.Level.All, System.out);
        EventLoopThread t = new EventLoopThread();
        t.setThreadInitFunction((e) -> {
            xs = new RUDPServer(new Timer(), e, 0);
            xs.setReadCallback((c, l) -> {
                var d = l.head();
                do {
                    System.out.println(c + ": " + bufferToString(d.v.b));
                    d = d.right();
                } while (d != null);
            });
            xs.init(new InetSocketAddress(45555));
        });
        t.start();
        t.join();
    }

    @Test
    public void y() throws Exception {
        EventLoopThread t = new EventLoopThread();
        t.setThreadInitFunction((e) -> {
            ys = new RUDPServer(new Timer(), e, 0);
            ys.setReadCallback((c, l) -> {
                var d = l.head();
                do {
                    System.out.println(c + ": " + bufferToString(d.v.b));
                    d = d.right();
                } while (d != null);
            });
            ys.init(new InetSocketAddress(45556));
        });
        t.start();
        t.join();
    }

    public static void main(String[] args) throws IOException {
        var remote = new InetSocketAddress("127.0.0.1", 45555);
        var local = new InetSocketAddress(45557);
        DatagramChannel c = DatagramChannel.open();
        c.bind(local);
        c.connect(remote);
        var th = new Thread(() -> {
            try {

                ByteBuffer b = ByteBuffer.allocate(512);
                while (c.read(b) > 0) {
                    b.flip();
                    System.out.println("read: " + bufferToString(b));
                    b.clear();
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        th.start();

        Scanner in = new Scanner(System.in);
        ByteBuffer b = ByteBuffer.allocate(512);

        while (true) {
            b.putInt(1);
            b.putInt(in.nextInt());
            b.put(in.next().getBytes());
            b.flip();
            c.write(b);
            b.clear();
        }

    }
}
