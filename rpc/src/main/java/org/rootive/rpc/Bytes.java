package org.rootive.rpc;

import java.nio.ByteBuffer;

public class Bytes implements Actor {
    private final ByteBuffer data;

    public Bytes(byte[] bytes) {
        data = Util.single(bytes, Type.Bytes);
    }

    public ByteBuffer getData() {
        return data.duplicate();
    }
}
