import org.junit.jupiter.api.Test;
import org.rootive.nio.SelectEventLoop;
import org.rootive.nio.rudp.RUDPConnection;
import org.rootive.nio.rudp.RUDPManager;
import org.rootive.nio.rudp.RUDPPieces;
import org.rootive.util.Linked;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;

public class RUDPTest {

    private void onRead(RUDPConnection connection, Linked<ByteBuffer> byteBuffers) {
        StringBuilder s = new StringBuilder();
        while (!byteBuffers.isEmpty()) {
            final ByteBuffer byteBuffer = byteBuffers.removeFirst();
            s.append(new String(byteBuffer.array(), byteBuffer.arrayOffset() + byteBuffer.position(), byteBuffer.remaining()));
            System.out.println(s);
        }
    }
    private void onReset(RUDPConnection connection) {
        System.out.println("reset");
    }

    @Test
    public void server() throws IOException, InterruptedException {
        final SelectEventLoop selectEventLoop = new SelectEventLoop();
        final RUDPManager rudpManager = new RUDPManager(selectEventLoop, 2);
        rudpManager.setReadCallback(this::onRead);
        rudpManager.setResetCallback(this::onReset);
        selectEventLoop.init();
        rudpManager.init(new InetSocketAddress(11115));

        new Timer().schedule(new TimerTask() {
            int count = 0;
            @Override
            public void run() {
                rudpManager.force(new InetSocketAddress("127.0.0.1", 11116), (connection) -> connection.message(new RUDPPieces(ByteBuffer.wrap(("receive " + count++).getBytes()))));
            }
        }, 2000, 2000);

        System.out.println("started");
        selectEventLoop.start();

    }

    @Test
    public void client() throws IOException, InterruptedException {
        final SelectEventLoop selectEventLoop = new SelectEventLoop();
        final RUDPManager rudpManager = new RUDPManager(selectEventLoop, 1);
        rudpManager.setReadCallback(this::onRead);
        rudpManager.setResetCallback(this::onReset);
        selectEventLoop.init();
        rudpManager.init(new InetSocketAddress(11116));

        new Timer().schedule(new TimerTask() {
            int count = 0;
            @Override
            public void run() {
                rudpManager.force(new InetSocketAddress("127.0.0.1", 11115), (connection) -> connection.message(new RUDPPieces(ByteBuffer.wrap(("receive " + count++).getBytes()))));
            }
        }, 2000, 2000);

        System.out.println("started");
        selectEventLoop.start();


    }
}
