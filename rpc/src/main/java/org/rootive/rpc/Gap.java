package org.rootive.rpc;

import java.nio.ByteBuffer;

public class Gap {
    public enum Context {
        CallLiteral, CallBytes,
        Return, TransmissionException, ParseException, InvocationException
    }

    static public final int contextSize = 9;

    static public ByteBuffer get(Context c, long _check) {
        return ByteBuffer.allocate(Constexpr.headerSize + contextSize)
                .putInt(contextSize)
                .put((byte) Type.Gap.ordinal())
                .put((byte) c.ordinal())
                .putLong(_check)
                .flip();
    }

    private Context ctx;
    private long check;

    public Context getContext() {
        return ctx;
    }
    public long getCheck() {
        return check;
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
        check = b.getLong();
    }
}
