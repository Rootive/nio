import org.junit.Test;
import org.rootive.log.Logger;
import org.rootive.nio.EventLoopThread;
import org.rootive.nio.RUDPConnection;
import org.rootive.rpc.ClientStub;
import org.rootive.rpc.Function;
import java.net.InetSocketAddress;


public class Peer {

    public String sayHelloWorld() {
        System.out.println("Hello, world.");
        return "done";
    }
    public String say(String s) {
        System.out.println(s);
        return s;
    }

    @Test
    public void x() throws Exception {
        Logger.start(Logger.Level.All, System.out);
        var local = new InetSocketAddress(45555);
        Function f = new Function(Peer.class, Peer.class.getMethod("sayHelloWorld"));
        Function fs = new Function(Peer.class, Peer.class.getMethod("say", String.class));

        EventLoopThread et = new EventLoopThread();
        et.setThreadInitFunction((e) -> {
            RUDPPeer p = new RUDPPeer(e, 1, 1);

            Peer peer = new Peer();
            p.register("peer", peer);
            p.register(f);
            p.register(fs);

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
        Function fs = new Function(Peer.class, Peer.class.getMethod("say", String.class));

        EventLoopThread et = new EventLoopThread();
        et.setThreadInitFunction((e) -> {
            yp = new RUDPPeer(e, 1, 1);

            Peer peer = new Peer();
            yp.register("peer", peer);
            yp.register(f);
            yp.register(fs);

            yp.init(local);
        });
        et.start();

        var invoker = ClientStub.func(fs).arg(ClientStub.sig(Peer.class, "peer"), ClientStub.sig(String.class, "address"));
        yp.force(remote, invoker);

        System.out.println(new String(invoker.ret()));

        Thread.sleep(5000);
        yp.disconnect(remote);
        et.join();
    }

}
