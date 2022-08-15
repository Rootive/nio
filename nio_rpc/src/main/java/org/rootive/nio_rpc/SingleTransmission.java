package org.rootive.nio_rpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.rootive.nio.TCPClient;
import org.rootive.rpc.Invoker;
import org.rootive.rpc.Result;
import org.rootive.rpc.Transmission;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.ReentrantLock;

public class SingleTransmission implements Transmission {
    private final TCPClient tcpClient;
    private final Queue<Invoker> queue = new LinkedList<>();
    private final ReentrantLock queueLock = new ReentrantLock();

    public SingleTransmission(TCPClient tcpClient) {
        this.tcpClient = tcpClient;
    }
    @Override
    public void toServer(byte[] data, Invoker client) {
        queueLock.lock();
        queue.add(client);
        tcpClient.queueWrite(ByteBuffer.wrap(data));
        queueLock.unlock();
    }

    public void handleRead(byte[] data) throws IOException {
        Invoker invoker;
        queueLock.lock();
        invoker = queue.remove();
        queueLock.unlock();
        invoker.setReturn(new ObjectMapper().readValue(data, Result.class));
    }
}
