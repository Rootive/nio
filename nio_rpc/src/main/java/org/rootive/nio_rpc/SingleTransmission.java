package org.rootive.nio_rpc;

import org.rootive.nio.TCPClient;
import org.rootive.rpc.Invoker;
import org.rootive.rpc.Transmission;

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

    public void handleRead(byte[] data) {
        Invoker invoker;
        queueLock.lock();
        invoker = queue.remove();
        queueLock.unlock();
        invoker.setReturn(data);
    }
}
