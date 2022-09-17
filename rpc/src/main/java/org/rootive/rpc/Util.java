package org.rootive.rpc;

import java.nio.ByteBuffer;

public final class Util {
    static public ByteBuffer single(byte[] bs, Type type) {
        return ByteBuffer
                .allocate(Constexpr.headerSize + bs.length)
                .putInt(bs.length)
                .put((byte) type.ordinal())
                .put(bs)
                .flip();
    }
}
