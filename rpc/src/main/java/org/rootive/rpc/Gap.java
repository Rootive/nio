package org.rootive.rpc;

import java.nio.ByteBuffer;

public class Gap {

    public final static byte CALL = 0b0000000, RETURN = 0b0000001; // call or return
    public final static byte TRANSMISSION_EXCEPTION = 0b0000010, PARSE_EXCEPTION = 0b0000100, INVOCATION_EXCEPTION = 0b0000110;
    public final static byte LITERAL = 0b0000000, BYTES = 0b0001000, KEEP = 0b0010000;
    public final static byte CONTINUE = 0b0000000, SINGLE = 0b0100000, BULK = 0b1000000, CHAIN = 0b1100000;

    static public final int contextSize = 9;
    static public final int keepSize = 1;

    public byte ctx;
    public long token;
    public String identifier;

    public Gap(byte ctx, long token, String identifier) {
        this.ctx = ctx;
        this.token = token;
        this.identifier = identifier;
    }

    public Gap(byte ctx, long token) {
        this.ctx = ctx;
        this.token = token;
    }
    public Gap(byte ctx, String identifier) {
        this.ctx = ctx;
        this.identifier = identifier;
    }
    public Gap(byte ctx) {
        this.ctx = ctx;
    }

    static public Gap parse(ByteBuffer byteBuffer) {
        if (byteBuffer.remaining() < contextSize) {
            return null;
        }

        var ctx = byteBuffer.get();
        var token = byteBuffer.getLong();

        String identifier = null;
        if ((ctx & 0b0011000) == KEEP) {
            if (byteBuffer.remaining() < keepSize) {
                return null;
            }
            var length = byteBuffer.get();
            if (byteBuffer.remaining() < length) {
                return null;
            }
            var bytes = new byte[length];
            byteBuffer.get(bytes);
            identifier = new String(bytes);
        }
        return new Gap(ctx, token, identifier);
    }

    public boolean isCall() {
        return (ctx & 0b0000001) == 0;
    }
    public byte getException() {
        return (byte) (ctx & 0b0000110);
    }
    public byte getAction() {
        return (byte) (ctx & 0b0011000);
    }
    public boolean isContinue() {
        return (ctx & 0b1100000) == 0;
    }
    public byte getStruct() { return (byte) (ctx & 0b1100000); }
    public void setStruct(byte struct) {
        ctx = (byte) ((ctx & ~0b1100000) | (struct & 0b1100000));
    }
    public ByteBuffer line() {
        if (getAction() == KEEP) {
            var bytes = identifier.getBytes();
            var lineSize = contextSize + Constexpr.typeSize + keepSize + bytes.length;
            return ByteBuffer
                    .allocate(Constexpr.sizeSize + lineSize)
                    .putInt(lineSize)
                    .put((byte) Type.Gap.ordinal())
                    .put(ctx)
                    .putLong(token)
                    .put((byte) bytes.length)
                    .put(bytes)
                    .flip();
        } else {
            var lineSize = contextSize + Constexpr.typeSize;
            return ByteBuffer
                    .allocate(Constexpr.sizeSize + lineSize)
                    .putInt(lineSize)
                    .put((byte) Type.Gap.ordinal())
                    .put(ctx)
                    .putLong(token)
                    .flip();
        }
    }


}
