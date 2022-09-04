package org.rootive.rpc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Return {
    public enum Status {
        Done,
        TransmissionException,
        ParseException,
        InvocationException
    }

    private byte stat;
    private Object data;
    private Function function;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private boolean bSet;

    public Return() { }
    public Return(Function function) {
        this.function = function;
    }

    public void clear(Function function) {
        this.function = function;
        bSet = false;
    }
    public void set(Status stat, Object data) {
        this.stat = (byte) stat.ordinal();
        this.data = data;

        lock.lock();
        bSet = true;
        condition.signal();
        lock.unlock();
    }
    public void set(Collector collector) {
        stat = collector.getStatus();

        var d = collector.getDone().removeFirst();
        if (d.get() == Type.Literal.ordinal()) {
            try {
                data = new ObjectMapper().readValue(new String(d.array(), d.arrayOffset() + d.position(), d.remaining()), function.getReturnClass());
            } catch (JsonProcessingException e) {
                data = e.getMessage() + " the origin status: " + stat;
                stat = (byte) Return.Status.ParseException.ordinal();
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

        if (stat < 0 || stat >= Return.Status.values().length) {
            throw new ParseException("unrecognized status: " + stat);
        }

        switch (Return.Status.values()[stat]) {
            case TransmissionException -> throw new TransmissionException(data.toString());
            case ParseException -> throw new ParseException(data.toString());
            case InvocationException -> throw new InvocationException(data.toString());
        }
        return data;
    }

    public boolean wait(int timeout) throws InterruptedException {
        boolean ret = true;
        lock.lock();
        while (!bSet) {
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
