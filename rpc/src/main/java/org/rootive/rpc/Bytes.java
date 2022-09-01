package org.rootive.rpc;

import java.nio.ByteBuffer;

public class Bytes implements Actor {
    private final ByteBuffer data;

    public Bytes(byte[] bytes) {
        data = ByteBuffer.allocate(bytes.length + Constexpr.headerSize)
                .putInt(bytes.length)
                .put((byte) Type.Bytes.ordinal())
                .put(bytes)
                .flip();
    }

    public ByteBuffer getData() {
        return data.duplicate();
    }
}
