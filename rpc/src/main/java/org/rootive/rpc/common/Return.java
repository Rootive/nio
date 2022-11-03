package org.rootive.rpc.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.rootive.rpc.Collector;
import org.rootive.rpc.Function;
import org.rootive.rpc.Gap;
import org.rootive.rpc.Type;
import org.rootive.rpc.common.InvocationException;
import org.rootive.rpc.common.ParseException;
import org.rootive.rpc.common.TransmissionException;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Return {
    private Function function;
    private long check;
    private boolean bSet;

    private Gap.Context ctx;
    private Object data;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();

    public void clear(Function function, long check) {
        this.function = function;
        this.check = check;
        bSet = false;
    }
    public long getCheck() {
        return check;
    }

    public void set(Gap.Context ctx, Object data) {
        this.ctx = ctx;
        this.data = data;

        lock.lock();
        bSet = true;
        condition.signal();
        lock.unlock();
    }
    public void set(Collector collector) {
        ctx = collector.getContext();
        var d = collector.getDone().removeFirst();
        if (d.get() == Type.Literal.ordinal()) {
            try {
                var s = new String(d.array(), d.arrayOffset() + d.position(), d.remaining());
                if (ctx == Gap.Context.Return) {
                    data = new ObjectMapper().readValue(s, function.getReturnClass());
                } else {
                    data = new ObjectMapper().readValue(s, String.class);
                }
            } catch (JsonProcessingException e) {
                data = e.getMessage();
                ctx = Gap.Context.ParseException;
            }
        } else {
            data = new byte[d.remaining()];
            System.arraycopy(d.array(), d.arrayOffset() + d.position(), (byte[]) data, 0, d.remaining());
        }
        lock.lock();
        bSet = true;
        condition.signal();
        lock.unlock();
    }

    public Object get() throws InterruptedException, ParseException, TransmissionException, InvocationException {
        lock.lock();
        while (!bSet) {
            condition.await();
        }
        lock.unlock();

        switch (ctx) {
            case TransmissionException -> throw new TransmissionException(data.toString());
            case ParseException -> throw new ParseException(data.toString());
            case InvocationException -> throw new InvocationException(data.toString());
        }
        return data;
    }

    public boolean wait(int timeout) throws InterruptedException {
        boolean ret = true;
        lock.lock();
        if (!bSet) {
            ret = condition.await(timeout, TimeUnit.MILLISECONDS);
        }
        lock.unlock();
        return ret;
    }
    public boolean isSet() {
        boolean ret;
        lock.lock();
        ret = bSet;
        lock.unlock();
        return ret;
    }
}
