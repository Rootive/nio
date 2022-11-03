package org.rootive.rpc.nio_rpc;

import org.rootive.rpc.nio.rudp.RUDPConnection;
import org.rootive.rpc.nio.rudp.RUDPPieces;
import org.rootive.rpc.server.ServerStub;
import org.rootive.rpc.*;

import java.nio.ByteBuffer;
import java.util.function.BiConsumer;

public class RUDPPeerStub {

    private final ServerStub serverStub;
    private final RUDPClientStub clientStub;
    private Collector collector = new Collector();

    public RUDPPeerStub(ServerStub parent, RUDPConnection connection) {
        serverStub = new ServerStub(parent, (d) -> connection.message(new RUDPPieces(d)));
        clientStub = new RUDPClientStub(connection);

        serverStub.register(Signature.namespaceStringOf(String.class), "address", connection.getRemote().toString().substring(1), false);
    }

    public RUDPClientStub getClientStub() {
        return clientStub;
    }
    public void setDispatcher(BiConsumer<String, Runnable> dispatcher) {
        serverStub.setDispatcher(dispatcher);
    }

    public void handleReceived(Linked<ByteBuffer> bs) {
        while (!bs.isEmpty()) {
            if (collector.collect(bs.removeFirst())) {
                var clone = collector;
                collector = new Collector();

                if (clone.getContext().compareTo(Gap.Context.Return) >= 0) {
                    clientStub.handleReceived(clone);
                } else {
                    serverStub.handleReceived(clone);
                }
            }
        }
    }
    public void drop() {
        clientStub.drop("dropped and not sent because connection reset");
    }

}
