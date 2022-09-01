package org.rootive.rpc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.rootive.gadgets.ByteArrayOutputStreamE;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Functor implements Actor {
    @FunctionalInterface public interface Transmission {
        void send(ByteBuffer data, Functor f) throws Exception;
    }
    static private final ByteBuffer callLiteralGap = Gap.get(Gap.Context.CallLiteral);
    static private final ByteBuffer callBytesGap = Gap.get(Gap.Context.CallBytes);
    static private final ByteBuffer callOnlyGap = Gap.get(Gap.Context.CallOnly);
    static private ByteBuffer callLiteralGap() {
        return callLiteralGap.duplicate();
    }
    static private ByteBuffer callBytesGap() {
        return callBytesGap.duplicate();
    }
    static private ByteBuffer callOnlyGap() {
        return callOnlyGap.duplicate();
    }

    private final Function func;
    private final ByteBuffer data;

    private Return ret;
    private ReentrantLock lock;
    private Condition condition;

    Functor(Function func, Signature sig, Object...parameters) throws IOException {
        this.func = func;
        var parameterClasses = func.getParameterClasses();
        assert parameterClasses.size() == parameters.length;
        for (var _i = 0; _i < parameters.length; ++_i) {
            var aClass = parameters[_i].getClass();
            assert parameterClasses.get(_i) == aClass || Actor.class.isAssignableFrom(aClass);
        }

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

    public Functor callLiteral(Transmission t) throws Exception {
        lock = new ReentrantLock();
        condition = lock.newCondition();
        t.send(getData(), this);
        t.send(callLiteralGap(), null);
        return this;
    }
    public Functor callBytes(Transmission t) throws Exception {
        lock = new ReentrantLock();
        condition = lock.newCondition();
        t.send(getData(), this);
        t.send(callBytesGap(), null);
        return this;
    }
    public Functor callOnly(Transmission t) throws Exception {
        t.send(getData(), null);
        t.send(callOnlyGap(), null);
        return this;
    }

    public void setReturn(Collector collector) {
        var res = new Return();
        res.stat = collector.getStatus();
        res.data = collector.getDone().removeFirst();

        lock.lock();
        ret = res;
        condition.signal();
        lock.unlock();
    }
    public Object ret() throws InterruptedException, ParseException, TransmissionException, InvocationException {
        lock.lock();
        while (ret == null) {
            condition.await();
        }
        lock.unlock();

        var d = (ByteBuffer) ret.data;
        if (d.get() == Type.Literal.ordinal()) {
            var json = new String(d.array(), d.arrayOffset() + d.position(), d.remaining());
            try {
                ret.data = new ObjectMapper().readValue(json, func.getReturnClass());
            } catch (JsonProcessingException e) {
                throw new ParseException("local: " + e.getMessage());
            }
        } else {
            ret.data = new byte[d.remaining()];
            System.arraycopy(d.array(), d.arrayOffset() + d.position(), (byte[]) ret.data, 0, d.remaining());
        }

        if (ret.stat < 0 || ret.stat >= Return.Status.values().length) {
            throw new ParseException("unrecognized status: " + ret.stat);
        }

        switch (Return.Status.values()[ret.stat]) {
            case TransmissionException -> throw new TransmissionException("remote: " + ret.data);
            case ParseException -> throw new ParseException("remote: " + ret.data);
            case InvocationException -> throw new InvocationException("remote: " + ret.data);
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
