import org.junit.jupiter.api.Test;
import org.rootive.distributed.raft.Constexpr;
import org.rootive.distributed.raft.RUDPRaft;
import org.rootive.nio.EventLoopThread;
import org.rootive.nio.PlainEventLoop;
import org.rootive.nio.SelectEventLoop;
import org.rootive.nio_rpc.RUDPPeer;

import java.io.IOException;

public class RaftTest {

    private void peer(int id) throws IOException, InterruptedException, NoSuchMethodException {
        final SelectEventLoop selectEventLoop = new SelectEventLoop();
        final RUDPPeer rudpPeer = new RUDPPeer(selectEventLoop, 2);
        final EventLoopThread eventLoopThread = new EventLoopThread(PlainEventLoop.class);
        eventLoopThread.start();
        final RUDPRaft rudpRaft = new RUDPRaft(rudpPeer, eventLoopThread.getEventLoop(), id, "D:/.tmp/" + id);
        rudpPeer.put("raft", eventLoopThread.getEventLoop());

        selectEventLoop.init();
        rudpPeer.init(Constexpr.addresses[id]);
        rudpRaft.init(bytes -> System.out.println(new String(bytes)));

        System.out.println("start");
        selectEventLoop.start();
    }

    @Test
    public void peer0() throws IOException, InterruptedException, NoSuchMethodException {
        peer(0);
    }

    @Test
    public void peer1() throws IOException, InterruptedException, NoSuchMethodException {
        peer(1);
    }

    @Test
    public void peer2() throws IOException, InterruptedException, NoSuchMethodException {
        peer(2);
    }
}
