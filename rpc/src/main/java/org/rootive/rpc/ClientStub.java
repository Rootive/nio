package org.rootive.rpc;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.concurrent.locks.ReentrantLock;

public abstract class ClientStub {
    private final HashMap<Long, Return> map = new HashMap<>();
    private final ReentrantLock lock = new ReentrantLock();

    protected abstract void send(ByteBuffer d);
    public void _send(ByteBuffer d, Return ret) {
        if (ret != null) {
            lock.lock();
            map.put(ret.getCheck(), ret);
            lock.unlock();
        }

        send(d);
    }
    public void handleReceived(Collector collector) {
        Return ret;
        lock.lock();
        ret = map.remove(collector.getCheck());
        lock.unlock();
        ret.set(collector);
    }
    public void drop(String msg) {
        lock.lock();
        map.forEach((check, ret) -> ret.set(Gap.Context.TransmissionException, msg));
        map.clear();
        lock.unlock();
    }
}
