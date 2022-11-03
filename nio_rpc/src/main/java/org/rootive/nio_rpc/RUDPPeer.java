package org.rootive.nio_rpc;

import org.rootive.nio.EventLoop;
import org.rootive.nio.SelectEventLoop;
import org.rootive.nio.rudp.RUDPConnection;
import org.rootive.nio.rudp.RUDPManager;
import org.rootive.rpc.Collector;
import org.rootive.rpc.client.ClientStub;
import org.rootive.util.Linked;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;

public class RUDPPeer extends RUDPServerStub {
    private final RUDPManager rudpManager;
    private final HashMap<String, EventLoop> map = new HashMap<>();

    public RUDPPeer(SelectEventLoop selectEventLoop, int threadsCount) {
        rudpManager = new RUDPManager(selectEventLoop, threadsCount);

        rudpManager.setReadCallback(this::onRead);
        rudpManager.setResetCallback(this::onReset);
        rudpManager.setContextSetter((connection) -> new RUDPPeerStub(this, connection.getEventLoop(), map));
    }
    public void init(InetSocketAddress local) throws IOException, InterruptedException {
        rudpManager.init(local);
    }
    public void put(String namespaceString, EventLoop eventLoop) {
        map.put(namespaceString, eventLoop);
    }

    public void force(SocketAddress remote, ClientStub.Session session, byte struct) {
        rudpManager.force(remote, (connection) -> {
            var clientStub = ((RUDPPeerStub) connection.context).clientStub;
            session.end(clientStub, connection, struct);
        });
    }

    private void onRead(RUDPConnection connection, Linked<ByteBuffer> byteBuffers) {
        //System.out.println("RUDPPeer onRead");
        var peerStub = (RUDPPeerStub) connection.context;
        while (!byteBuffers.isEmpty()) {
            final ByteBuffer byteBuffer = byteBuffers.removeFirst();
            while (byteBuffer.remaining() > 0) {
                final List<Collector> collectors = peerStub.serverStub.collect(byteBuffer);
                if (collectors != null && !collectors.isEmpty()) {
                    //System.out.println("RUDPPeer " + (collectors.get(0).getGap().isCall() ? "call" : "return"));
                    if (collectors.get(0).getGap().isCall()) {
                        peerStub.serverStub.handle(connection, collectors);
                    } else {
                        peerStub.clientStub.handle(collectors);
                    }
                }
            }
        }
    }
    private void onReset(RUDPConnection connection) {
        var peerStub = (RUDPPeerStub) connection.context;
        peerStub.clientStub.dropAll("dropped because of connection reset");
    }
}
