package org.rootive.nio_rpc;

import org.rootive.nio.RUDPConnection;
import org.rootive.rpc.Collector;
import org.rootive.rpc.Functor;
import org.rootive.rpc.Return;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.ReentrantLock;

public class RUDPTransmission {
    private final RUDPConnection connection;
    private final Queue<Functor> queue = new LinkedList<>();
    private final ReentrantLock queueLock = new ReentrantLock();

    public RUDPTransmission(RUDPConnection c) {
        this.connection = c;
    }

    public RUDPConnection getConnection() {
        return connection;
    }

    public void send(ByteBuffer data, Functor f) {
        if (f != null) {
            queueLock.lock();
            queue.add(f);
            queueLock.unlock();
        }
        connection.message(data);
        connection.flush();
    }
    public void handle(Collector collector) {
        Functor f;
        queueLock.lock();
        f = queue.remove();
        queueLock.unlock();
        f.setReturn(collector);
    }
    public void drop(String msg) {
        var res = new Return(Return.Status.TransmissionException, msg);
        queueLock.lock();
        while (queue.size() > 0) {
            queue.remove().setReturn(res);
        }
        queueLock.unlock();
    }
}
