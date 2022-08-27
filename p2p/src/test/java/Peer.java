import org.junit.Test;
import org.rootive.log.Logger;
import org.rootive.nio.EventLoopThread;
import org.rootive.rpc.ClientStub;
import org.rootive.rpc.Function;
import java.net.InetSocketAddress;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


public class Peer {

    public String sayHelloWorld() {
        System.out.println("Hello, world.");
        return "done";
    }

    @Test
    public void x() throws Exception {
        Logger.start(Logger.Level.All, System.out);
        var local = new InetSocketAddress(45555);
        Function f = new Function(Peer.class, Peer.class.getMethod("sayHelloWorld"));

        EventLoopThread et = new EventLoopThread();
        et.setThreadInitFunction((e) -> {
            RUDPPeer p = new RUDPPeer(e, 1, 1);

            Peer peer = new Peer();
            p.register("peer", peer);
            p.register(f);

            p.init(local);
        });
        et.start();



        et.join();
    }

    RUDPPeer yp;
    @Test
    public void y() throws Exception {
        Logger.start(Logger.Level.All, System.out);
        var local = new InetSocketAddress(45556);
        var remote = new InetSocketAddress("127.0.0.1", 45555);
        Function f = new Function(Peer.class, Peer.class.getMethod("sayHelloWorld"));

        EventLoopThread et = new EventLoopThread();
        et.setThreadInitFunction((e) -> {
            yp = new RUDPPeer(e, 1, 1);

            Peer peer = new Peer();
            yp.register("peer", peer);
            yp.register(f);

            yp.init(local);
        });
        et.start();

        var ret = ClientStub.func(f).arg(ClientStub.sig(Peer.class, "peer")).invoke(yp.get(remote)).ret();

        System.out.println(new String(ret));

        et.join();
    }

    @Test
    public void timers() throws InterruptedException {
        var s = new ScheduledThreadPoolExecutor(1);
        var f = s.scheduleAtFixedRate(() -> System.out.println("x"), 0, 1000, TimeUnit.MILLISECONDS);
        s.scheduleAtFixedRate(() -> System.out.println("y"), 0, 2000, TimeUnit.MILLISECONDS);
        Thread.sleep(2000);
        f.cancel(false);
        Thread.sleep(8000);
    }
}
