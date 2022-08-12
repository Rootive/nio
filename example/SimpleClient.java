import org.rootive.nio.TCPClient;
import org.rootive.nio.TCPConnection;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Scanner;

public class SimpleClient {
    static private void onRead(TCPClient client, TCPConnection c) {
        var buffers = c.getReadBuffers();
        while (buffers.size() > 0) {
            var buffer = buffers.removeFirst();
            String s = new String(buffer.array(), buffer.position() + buffer.arrayOffset(), buffer.remaining());
            System.out.print(s);
        }
    }
    public static void main(String[] args) throws Exception {
        TCPClient client = new TCPClient();
        client.setReadCallback(SimpleClient::onRead);
        client.init();
        client.open(new InetSocketAddress("127.0.0.1", 45555));
        Thread th = new Thread(() -> {
            Scanner in = new Scanner(System.in);
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            while (true) {
                String s = in.nextLine() + '\n';
                if (s.charAt(0) == '@') {
                    client.queueDisconnect();
                } else {
                    buffer.clear();
                    buffer.put(s.getBytes());
                    buffer.limit(buffer.position());
                    buffer.position(0);
                    client.queueWrite(buffer);
                }
            }
        });
        th.start();
        client.start();
    }
}
