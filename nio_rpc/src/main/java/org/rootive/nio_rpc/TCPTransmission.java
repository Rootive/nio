package org.rootive.nio_rpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.rootive.nio.TCPConnection;
import org.rootive.rpc.ClientStub;
import org.rootive.rpc.Invoker;
import org.rootive.rpc.Result;
import org.rootive.rpc.Transmission;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.ReentrantLock;

public class TCPTransmission implements Transmission {
    private final TCPConnection connection;
    private final Queue<Invoker> queue = new LinkedList<>();
    private final ReentrantLock queueLock = new ReentrantLock();

    public TCPTransmission(TCPConnection connection) {
        this.connection = connection;
    }
    @Override
    public void send(byte[] data, Invoker invoker) {
        queueLock.lock();
        queue.add(invoker);
        queueLock.unlock();
        connection.queueWrite(ByteBuffer.wrap(data).slice(ClientStub.headerSize, data.length - ClientStub.headerSize));
    }

    public void handleRead(byte[] data) throws IOException {
        Invoker _invoker;
        queueLock.lock();
        _invoker = queue.remove();
        queueLock.unlock();
        _invoker.setReturn(new ObjectMapper().readValue(data, Result.class));
    }
}
