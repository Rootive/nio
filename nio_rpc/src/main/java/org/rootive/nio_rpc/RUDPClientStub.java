package org.rootive.nio_rpc;

import org.rootive.nio.rudp.RUDPConnection;
import org.rootive.nio.rudp.RUDPPieces;
import org.rootive.rpc.client.ClientStub;

import java.nio.ByteBuffer;

public class RUDPClientStub extends ClientStub {
    private final RUDPConnection connection;

    public RUDPClientStub(RUDPConnection c) {
        this.connection = c;
    }

    @Override
    protected void send(ByteBuffer d) {
        connection.message(new RUDPPieces(d));
    }
}
