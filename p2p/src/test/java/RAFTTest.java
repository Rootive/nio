import org.rootive.nio.EventLoopThread;
import org.rootive.nio.LoopThread;
import org.rootive.p2p.RUDPPeer;
import org.rootive.p2p.raft.Constexpr;
import org.rootive.p2p.raft.RUDPRaft;
import org.rootive.rpc.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Scanner;

public class RAFTTest {
    public void server(int id) throws InterruptedException, IOException {
        EventLoopThread peerThread = new EventLoopThread();
        peerThread.start();
        LoopThread raftThread = new LoopThread();
        raftThread.start();

        RUDPPeer peer = new RUDPPeer(peerThread.getEventLoop(), 2);
        peer.setDispatcher((s, r) -> raftThread.getLoop().run(r));
        RUDPRaft raft = new RUDPRaft(peer, raftThread.getLoop(), id, "D:/.tmp/test/" + id);

        raft.init((d) -> System.out.println(new String(d)));
        peer.init(Constexpr.addresses[id]);
    }

    public void client() throws NoSuchMethodException, InterruptedException, IOException {
        Signature raftSig = new Signature(RUDPRaft.class, "raft");
        Function sendFunc = new Function(RUDPRaft.class.getMethod("send", byte[].class));
        Signature sendSig = new Signature(sendFunc, "send");

        EventLoopThread peerThread = new EventLoopThread();
        peerThread.start();

        RUDPPeer peer = new RUDPPeer(peerThread.getEventLoop(), 1);
        peer.init(new InetSocketAddress(46555));
        var leaderId = 0;

        var in = new Scanner(System.in);
        while (true) {
            var functor = sendFunc.newFunctor(sendSig, raftSig, in.nextLine().getBytes());
            while (true) {
                var ret = peer.callLiteral(Constexpr.addresses[leaderId], functor);
                try {
                    if (ret.wait(4000)) {
                        int res = (int) ret.get();
                        if (res == leaderId) {
                            System.out.println("sent");
                            break;
                        } else if (res >= 0) {
                            leaderId = res;
                            System.out.println("redirect to " + leaderId);
                        } else {
                            System.out.println("election and wait");
                            Thread.sleep(2000);
                            System.out.println("\tcontinue");
                        }
                    } else {
                        leaderId = (leaderId + 1) % Constexpr.addresses.length;
                        System.out.println("timeout and try " + leaderId);
                    }
                } catch (TransmissionException | InvocationException | ParseException e) {
                    e.printStackTrace();
                }

            }
        }
    }
}
