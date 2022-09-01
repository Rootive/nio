package org.rootive.nio_rpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.rootive.nio.RUDPConnection;
import org.rootive.rpc.Functor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.ReentrantLock;

public class RUDPTransmission implements Transmission {
    private final RUDPConnection connection;
    private final Queue<Functor> queue = new LinkedList<>();
    private final ReentrantLock queueLock = new ReentrantLock();

    public RUDPTransmission(RUDPConnection c) {
        this.connection = c;
    }

    public RUDPConnection getConnection() {
        return connection;
    }

    @Override
    public void send(ByteBuffer data, Functor f) throws Exception {
        queueLock.lock();
        queue.add(f);
        queueLock.unlock();
        connection.message(data);
        connection.flush();
    }
    public void handle(String data) throws IOException {
        Functor f;
        queueLock.lock();
        f = queue.remove();
        queueLock.unlock();
        f.setReturn(new ObjectMapper().readValue(data, Result.class));
    }
    public void drop(String msg) {
        queueLock.lock();
        while (queue.size() > 0) {
            var f = queue.remove();
            f.setReturn(new Result(Result.Status.BAD_TRANSMISSION, msg));
        }
        queueLock.unlock();
    }
}
