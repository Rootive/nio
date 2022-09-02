package org.rootive.nio_rpc;

import org.rootive.util.Linked;
import org.rootive.nio.RUDPConnection;
import org.rootive.rpc.*;

import java.nio.ByteBuffer;
import java.util.function.BiConsumer;

public class RUDPPeerStub {

    private final ServerStub stub;
    private final RUDPTransmission transmission;
    private final Collector collector = new Collector();

    public RUDPPeerStub(ServerStub p, RUDPConnection connection) {
        stub = new ServerStub(p, connection::message);
        transmission = new RUDPTransmission(connection);
        stub.register(String.class, "address", connection.getRemote().toString());
    }

    public BiConsumer<ByteBuffer, Functor> getTransmission() {
        return transmission::send;
    }

    public void handleReceived(Linked<ByteBuffer> bs) {
        while (!bs.isEmpty()) {
            if (collector.collect(bs.removeFirst())) {
                if (collector.getContext() == Gap.Context.Return) {
                    transmission.handle(collector);
                } else {
                    stub.run(collector);
                }
                collector.clear();
            }
        }
    }
    public void disconnect() {
        transmission.drop("dropped and not sent because connection disconnected");
    }

}
