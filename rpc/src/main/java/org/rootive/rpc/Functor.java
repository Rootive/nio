package org.rootive.rpc;

import org.rootive.rpc.client.ClientStub;
import org.rootive.rpc.common.Return;

import java.io.IOException;
import java.nio.ByteBuffer;

public class Functor implements Actor {
    private final Function function;
    private final ByteBuffer data;

    Functor(Function function, Signature sig, Object...parameters) throws IOException {
        this.function = function;

        assert parameters.length == function.getParameterClasses().length + 1;

        ByteArrayOutputStreamE output = new ByteArrayOutputStreamE();
        output.write(sig.getData());
        for (var p : parameters) {
            if (p instanceof Actor a) {
                output.write(a.getData());
            } else {
                output.write(new Literal(p).getData());
            }
        }

        data = ByteBuffer.wrap(output.getBuf(), 0, output.size());
    }

    public void callLiteral(ClientStub stub, Return ret) {
        var check = System.currentTimeMillis();
        ret.clear(function, check);
        stub._send(getData(), ret);
        stub._send(Gap.get(Gap.Context.CallLiteral, check), null);
    }
    public void callBytes(ClientStub stub, Return ret) {
        var check = System.currentTimeMillis();
        ret.clear(function, check);
        stub._send(getData(), ret);
        stub._send(Gap.get(Gap.Context.CallBytes, check), null);
    }

    @Override
    public ByteBuffer getData() {
        return data.duplicate();
    }

}
