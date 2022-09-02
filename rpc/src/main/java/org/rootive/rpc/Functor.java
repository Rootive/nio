package org.rootive.rpc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.rootive.util.ByteArrayOutputStreamE;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
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

    private final Function func;
    private final ByteBuffer data;

    private Return ret;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();

    Functor(Function func, Signature sig, Object...parameters) throws IOException {
        this.func = func;

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

    public Functor callLiteral(BiConsumer<ByteBuffer, Functor> t) {
        t.accept(getData(), this);
        t.accept(callLiteralGap(), null);
        return this;
    }
    public Functor callBytes(BiConsumer<ByteBuffer, Functor> t) {
        t.accept(getData(), this);
        t.accept(callBytesGap(), null);
        return this;
    }


    public void setReturn(Collector collector) {
        var res = new Return();
        res.stat = collector.getStatus();

        var d = collector.getDone().removeFirst();
        if (d.get() == Type.Literal.ordinal()) {
            var json = new String(d.array(), d.arrayOffset() + d.position(), d.remaining());
            try {
                res.data = new ObjectMapper().readValue(json, func.getReturnClass());
            } catch (JsonProcessingException e) {
                res.data = e.getMessage();
                res.stat = (byte) Return.Status.ParseException.ordinal();
            }
        } else {
            res.data = new byte[d.remaining()];
            System.arraycopy(d.array(), d.arrayOffset() + d.position(), (byte[]) ret.data, 0, d.remaining());
        }

        setReturn(res);
    }
    public void setReturn(Return ret) {
        lock.lock();
        this.ret = ret;
        condition.signal();
        lock.unlock();
    }
    public Object ret() throws InterruptedException, ParseException, TransmissionException, InvocationException {

        lock.lock();
        while (ret == null) {
            condition.await();
        }
        lock.unlock();

        if (ret.stat < 0 || ret.stat >= Return.Status.values().length) {
            throw new ParseException("unrecognized status: " + ret.stat);
        }

        switch (Return.Status.values()[ret.stat]) {
            case TransmissionException -> throw new TransmissionException(ret.data.toString());
            case ParseException -> throw new ParseException(ret.data.toString());
            case InvocationException -> throw new InvocationException(ret.data.toString());
        }
        return ret.data;
    }
    public boolean isReturned() {
        boolean r;
        lock.lock();
        r = ret != null;
        lock.unlock();
        return r;
    }

    @Override
    public ByteBuffer getData() {
        return data.duplicate();
    }

}
