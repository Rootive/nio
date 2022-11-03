package org.rootive.rpc;

import java.nio.ByteBuffer;

public class Bytes implements Line {

    private final ByteBuffer byteBuffer;

    public Bytes(ByteBuffer byteBuffer) {
        this.byteBuffer = Util.line(Type.Bytes, byteBuffer);
    }
    @Override
    public ByteBuffer toByteBuffer() {
        return byteBuffer.duplicate();
    }
}
