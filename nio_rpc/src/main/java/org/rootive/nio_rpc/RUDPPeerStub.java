package org.rootive.nio_rpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.rootive.gadget.Linked;
import org.rootive.nio.RUDPConnection;
import org.rootive.rpc.*;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class RUDPPeerStub {
    static private final Result r = new Result(Result.Status.BAD_TRANSMISSION, "disconnected");

    private final ServerStub serverStub;
    private final RUDPTransmission transmission;
    private final Collecter collecter = new Collecter();

    public RUDPPeerStub(ServerStub p, RUDPConnection connection) {
        serverStub = new ServerStub(p);
        transmission = new RUDPTransmission(connection);
        serverStub.register(new Signature(String.class, "address"), connection.getRemote().toString());
    }

    public RUDPTransmission getTransmission() {
        return transmission;
    }

    public void handleReceived(Linked<ByteBuffer> bs) throws Exception {
        while (!bs.isEmpty()) {
            if (collecter.collect(bs) == Collecter.State.Done) {
                String s = new String(collecter.toByteArray());
                Parser p = new Parser(s);
                if (p.getType() == Parser.Type.Literal) {
                    transmission.handle(s);
                } else {

                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream(RUDPConnection.MTU);
                    for (var _i = 0; _i < RUDPConnection.headerSize; ++_i) {
                        outputStream.write(0);
                    }
                    new ObjectMapper().writeValue(outputStream, serverStub.invoke(p));
                    outputStream.write(';');
                    var b = ByteBuffer.wrap(outputStream.toByteArray());
                    transmission.getConnection().message(b.slice(RUDPConnection.headerSize, b.remaining() - RUDPConnection.headerSize));

                }
                collecter.clear();
            }
        }
    }
    public void disconnect() {
        transmission.handleAllWith(r);
    }

}
