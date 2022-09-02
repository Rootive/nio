package org.rootive.rpc;

import java.nio.ByteBuffer;

public class Gap {
    public enum Context {
        CallLiteral, CallBytes, Return
    }

    static public final int contextSize = 1;
    static public final int statusSize = 1;

    static public ByteBuffer get(Context c) {
        return ByteBuffer.wrap(
                        new byte[Constexpr.pre + Constexpr.headerSize + contextSize + Constexpr.post]
                        , Constexpr.pre
                        , Constexpr.headerSize + contextSize
                )
                .mark()
                .putInt(contextSize)
                .put((byte) Type.Gap.ordinal())
                .put((byte) c.ordinal())
                .reset();
    }
    static public ByteBuffer get(Return.Status stat) {
        return ByteBuffer.wrap(
                        new byte[Constexpr.pre + Constexpr.headerSize + contextSize + statusSize + Constexpr.post]
                        , Constexpr.pre
                        , Constexpr.headerSize + contextSize + statusSize
                )
                .mark()
                .putInt(contextSize)
                .put((byte) Type.Gap.ordinal())
                .put((byte) Context.Return.ordinal())
                .put((byte) stat.ordinal())
                .reset();
    }

    private Context ctx;
    private byte status;

    public Context getContext() {
        return ctx;
    }
    public byte getStatus() {
        return status;
    }

    void parse(ByteBuffer b) {
        if (b.remaining() < Constexpr.typeSize + contextSize) {
            return;
        }
        b.get();

        var c = b.get();
        if (c < 0 || Context.values().length <= c) {
            return;
        }
        ctx = Context.values()[c];

        if (ctx == Context.Return && b.remaining() >= statusSize) {
            status = b.get();
        }
    }
}
