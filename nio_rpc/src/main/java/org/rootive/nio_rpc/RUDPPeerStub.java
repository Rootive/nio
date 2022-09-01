package org.rootive.nio_rpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.rootive.gadgets.ByteArrayOutputStreamE;
import org.rootive.gadgets.Linked;
import org.rootive.nio.RUDPConnection;
import org.rootive.rpc.*;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class RUDPPeerStub {

    private final ServerStub stub;
    private final RUDPTransmission transmission;
    private final Collector collector = new Collector();

    public RUDPPeerStub(ServerStub p, RUDPConnection connection) {
        stub = new ServerStub(p);
        transmission = new RUDPTransmission(connection);
        stub.register(String.class, "address", connection.getRemote().toString());
    }

    public RUDPTransmission getTransmission() {
        return transmission;
    }

    public void handleReceived(Linked<ByteBuffer> bs) throws Exception {
        while (!bs.isEmpty()) {
            if (collector.collect(bs.removeFirst())) {
                if (collector.getDone().head().get() == Type.Signature.ordinal()) {
                    ByteArrayOutputStreamE output = new ByteArrayOutputStreamE();
                    for (var _i = 0; _i < RUDPConnection.headerSize; ++_i) {
                        output.write(0);
                    }
                    var res = new ObjectMapper().writeValueAsBytes(stub.run(collector));


                } else {

                }

//                 else {
//
//                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream(RUDPConnection.MTU);
//                    for (var _i = 0; _i < RUDPConnection.headerSize; ++_i) {
//                        outputStream.write(0);
//                    }
//                    new ObjectMapper().writeValue(outputStream, stub.invoke(p));
//                    outputStream.write(';');
//                    var b = ByteBuffer.wrap(outputStream.toByteArray());
//                    transmission.getConnection().message(b.slice(RUDPConnection.headerSize, b.remaining() - RUDPConnection.headerSize));
//
//                }
                collector.clear();
            }
        }
    }
    public void disconnect() {
        transmission.drop("dropped and not sent because connection disconnected");
    }

}
