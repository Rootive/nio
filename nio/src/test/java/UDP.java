import org.junit.Test;
import org.rootive.nio.EventLoopThread;
import org.rootive.nio.ReUDPServer;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class UDP {
    ReUDPServer as;
    ReUDPServer bs;

    String bufferToString(ByteBuffer b) {
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
            as = new ReUDPServer(e);
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
            bs = new ReUDPServer(e);
            bs.setConfirmedCallback(this::onConfirm);
            bs.setDropCallback(this::onDrop);
            bs.setReadCallback(this::onRead);
            bs.init(new InetSocketAddress(45556));
        });
        t.start();
        t.join();
    }


}
