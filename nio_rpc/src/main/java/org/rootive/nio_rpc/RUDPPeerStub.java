package org.rootive.nio_rpc;

import org.rootive.util.Linked;
import org.rootive.nio.RUDPConnection;
import org.rootive.rpc.*;

import java.nio.ByteBuffer;
import java.util.function.BiConsumer;

public class RUDPPeerStub {

    private final ServerStub stub;
    private final RUDPTransmission transmission;
    private Collector collector = new Collector();

    public RUDPPeerStub(ServerStub parent, RUDPConnection connection) {
        stub = new ServerStub(parent, (d) -> {
            connection.message(d);
            connection.flush();
        });
        transmission = new RUDPTransmission(connection);

        stub.register(String.class, "address", connection.getRemote().toString().substring(1));
    }

    public BiConsumer<ByteBuffer, Return> getTransmission() {
        return transmission::send;
    }
    public void setDispatcher(BiConsumer<String, Runnable> dispatcher) {
        stub.setDispatcher(dispatcher);
    }

    public void handleReceived(Linked<ByteBuffer> bs) {
        while (!bs.isEmpty()) {
            if (collector.collect(bs.removeFirst())) {
                var clone = collector;
                collector = new Collector();

                if (clone.getContext() == Gap.Context.Return) {
                    transmission.handleReceived(clone);
                } else {
                    stub.handleReceived(clone);
                }
            }
        }
    }
    public void drop() {
        transmission.drop("dropped and not sent because connection disconnected");
    }

}
