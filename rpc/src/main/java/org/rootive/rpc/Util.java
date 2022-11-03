package org.rootive.rpc;

import java.nio.ByteBuffer;

public class Util {
    static public ByteBuffer line(Type type, ByteBuffer byteBuffer) {
        var lineSize = byteBuffer.remaining() + Constexpr.typeSize;
        return ByteBuffer
                .allocate(Constexpr.sizeSize + lineSize)
                .putInt(lineSize)
                .put((byte) type.ordinal())
                .put(byteBuffer)
                .flip();
    }
}
