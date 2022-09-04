package org.rootive.rpc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.rootive.util.ByteArrayOutputStreamE;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.BiConsumer;

public class Functor implements Actor {
    static private final ByteBuffer callLiteralGap = Gap.get(Gap.Context.CallLiteral);
    static private final ByteBuffer callBytesGap = Gap.get(Gap.Context.CallBytes);
    static private ByteBuffer callLiteralGap() {
        return callLiteralGap.duplicate();
    }
    static private ByteBuffer callBytesGap() {
        return callBytesGap.duplicate();
    }

    private final Function function;
    private final ByteBuffer data;

    Functor(Function function, Signature sig, Object...parameters) throws IOException {
        this.function = function;

        ByteArrayOutputStreamE output = new ByteArrayOutputStreamE();

        for (var _i = 0; _i < Constexpr.pre; ++_i) {
            output.write(0);
        }

        output.write(sig.getData());
        for (var p : parameters) {
            if (p instanceof Actor a) {
                output.write(a.getData());
            } else {
                output.write(new Literal(p).getData());
            }
        }

        var p = Math.max(0, Constexpr.post - (output.getBuf().length - output.size()));
        for (var _i = 0; _i < p; ++_i) {
            output.write(0);
        }

        data = ByteBuffer.wrap(output.getBuf(), Constexpr.pre, output.size() - Constexpr.pre - p);
    }

    public Return callLiteral(BiConsumer<ByteBuffer, Return> t) {
        var ret = new Return(function);
        t.accept(getData(), ret);
        t.accept(callLiteralGap(), null);
        return ret;
    }
    public Return callBytes(BiConsumer<ByteBuffer, Return> t) {
        var ret = new Return(function);
        t.accept(getData(), ret);
        t.accept(callBytesGap(), null);
        return ret;
    }
    public void callLiteral(BiConsumer<ByteBuffer, Return> t, Return ret) {
        ret.clear(function);
        t.accept(getData(), ret);
        t.accept(callLiteralGap(), null);
    }
    public void callBytes(BiConsumer<ByteBuffer, Return> t, Return ret) {
        ret.clear(function);
        t.accept(getData(), ret);
        t.accept(callBytesGap(), null);
    }

    @Override
    public ByteBuffer getData() {
        return data.duplicate();
    }

}
