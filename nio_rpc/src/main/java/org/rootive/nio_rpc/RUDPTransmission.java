package org.rootive.nio_rpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.rootive.nio.RUDPConnection;
import org.rootive.rpc.ClientStub;
import org.rootive.rpc.Invoker;
import org.rootive.rpc.Result;
import org.rootive.rpc.Transmission;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.ReentrantLock;

public class RUDPTransmission implements Transmission {
    private final RUDPConnection connection;
    private final Queue<Invoker> queue = new LinkedList<>();
    private final ReentrantLock queueLock = new ReentrantLock();

    public RUDPTransmission(RUDPConnection c) {
        this.connection = c;
    }

    public RUDPConnection getConnection() {
        return connection;
    }

    @Override
    public void send(byte[] data, Invoker invoker) throws Exception {
        queueLock.lock();
        queue.add(invoker);
        queueLock.unlock();
        connection.message(ByteBuffer.wrap(data).slice(ClientStub.headerSize, data.length - ClientStub.headerSize));
        connection.flush();
    }
    public void handle(String data) throws IOException {
        Invoker invoker;
        queueLock.lock();
        invoker = queue.remove();
        queueLock.unlock();
        invoker.setReturn(new ObjectMapper().readValue(data, Result.class));
    }
    public void handleAllWith(Result result) {
        queueLock.lock();
        while (queue.size() > 0) {
            var invoker = queue.remove();
            invoker.setReturn(result);
        }
        queueLock.unlock();
    }
}
