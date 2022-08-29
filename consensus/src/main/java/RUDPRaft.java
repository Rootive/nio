import org.rootive.gadget.Linked;
import org.rootive.nio.EventLoopThread;

import java.net.InetSocketAddress;
import java.util.ArrayList;

public class RUDPRaft {
    private final EventLoopThread pt = new EventLoopThread();
    private RUDPPeer p;

    private final Raft.State state = Raft.State.Follower;

    private int currentTerm;
    private int votedFor;
    private Linked<String> logs;

    private int commitIndex;
    private int lastApplied;

    private ArrayList<Integer> nextIndex;
    private ArrayList<Integer> matchIndex;

    public RUDPRaft(int timersCount, int threadsCount, InetSocketAddress local) {
        pt.setThreadInitFunction((e) -> {
            p = new RUDPPeer(e, timersCount, threadsCount);
            p.init(local);
        });
    }
    public void init() throws Exception {
        pt.start();
    }
}
