package org.rootive.nio_rpc;

import org.rootive.nio.rudp.RUDPConnection;
import org.rootive.nio.rudp.RUDPPieces;
import org.rootive.rpc.client.ClientStub;

import java.nio.ByteBuffer;

public class RUDPClientStub extends ClientStub {

    @Override
    protected void send(Object with, ByteBuffer[] byteBuffer) {
        ((RUDPConnection) with).message(new RUDPPieces(byteBuffer));
    }
}
