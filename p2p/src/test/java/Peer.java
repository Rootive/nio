import org.junit.Test;
import org.rootive.log.Logger;
import org.rootive.nio.EventLoopThread;
import org.rootive.rpc.Function;
import org.rootive.rpc.Signature;

import java.io.IOException;
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
            p.register("sayHelloWorld", f);
            p.register("say", fs);

            try {
                p.init(local);
            } catch (IOException | InterruptedException ex) {
                ex.printStackTrace();
            }
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
            yp.register("sayHelloWorld", f);
            yp.register("say", fs);

            try {
                yp.init(local);
            } catch (IOException | InterruptedException ex) {
                ex.printStackTrace();
            }
        });
        et.start();

        var functor = fs.newFunctor(new Signature(fs, "say"), new Signature(this, "peer"), new Signature(String.class, "address"));
        yp.force(remote, functor);

        System.out.println(functor.ret().toString());

        et.join();
    }

}
