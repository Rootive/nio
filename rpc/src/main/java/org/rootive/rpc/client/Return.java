package org.rootive.rpc.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.rootive.rpc.Collector;
import org.rootive.rpc.Gap;
import org.rootive.rpc.Type;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Return <T> {
    private final Class<T> ret;
    private boolean bSet;
    private Object data;
    private Gap gap;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();


    public Return(Class<T> ret) {
        this.ret = ret;
    }

    public void set(Gap gap, Object data) {
        this.gap = gap;
        this.data = data;

        lock.lock();
        bSet = true;
        condition.signal();
        lock.unlock();
    }
    public void set(Collector collector) {
        gap = collector.getGap();
        var done = collector.getDone();
        if (gap == null) {
            gap = new Gap((byte) (Gap.RETURN | Gap.PARSE_EXCEPTION), 0);
        } else {
            var byteBuffer = done.removeFirst();

            if (byteBuffer.get() == Type.Literal.ordinal()) {
                if (gap.getException() == 0) {
                    try {
                        data = new ObjectMapper().readValue(byteBuffer.array(), byteBuffer.arrayOffset() + byteBuffer.position(), byteBuffer.remaining(), ret);
                    } catch (IOException e) {
                        gap = new Gap((byte) (Gap.RETURN | Gap.PARSE_EXCEPTION | gap.getStruct()), gap.token);
                        data = e.getMessage();
                    }
                } else {
                    data = new String(byteBuffer.array(), byteBuffer.arrayOffset() + byteBuffer.position(), byteBuffer.remaining());
                }
            } else {
                data = new byte[byteBuffer.remaining()];
                System.arraycopy(byteBuffer.array(), byteBuffer.arrayOffset() + byteBuffer.position(), (byte[]) data, 0, byteBuffer.remaining());
            }
        }
        lock.lock();
        bSet = true;
        condition.signal();
        lock.unlock();
    }

    public T get() throws InterruptedException, ParseException, TransmissionException, InvocationException {
        lock.lock();
        while (!bSet) {
            condition.await();
        }
        lock.unlock();

        switch (gap.getException()) {
            case Gap.TRANSMISSION_EXCEPTION -> throw new TransmissionException(new String((byte[]) data));
            case Gap.PARSE_EXCEPTION -> throw new ParseException(new String((byte[]) data));
            case Gap.INVOCATION_EXCEPTION -> throw new InvocationException(new String((byte[]) data));
        }
        return (T) data;
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