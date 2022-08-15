package org.rootive.nio_rpc;

import org.rootive.log.LogLine;
import org.rootive.log.Logger;
import org.rootive.nio.TCPClient;
import org.rootive.nio.TCPConnection;
import org.rootive.rpc.ClientStub;
import org.rootive.rpc.Collecter;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public class Client {
    private final TCPClient tcpClient = new TCPClient();
    private final SingleTransmission transmission = new SingleTransmission(tcpClient);
    private final ClientStub stub = new ClientStub(transmission);
    private final Collecter collecter = new Collecter();

    public Client() {
        tcpClient.setReadCallback(this::onRead);
        tcpClient.setConnectionCallback(this::onConnection);
    }
    public ClientStub getStub() {
        return stub;
    }

    public void init() throws IOException, InterruptedException {
        init(Logger.Level.All, System.out);
    }
    public void init(Logger.Level level, OutputStream output) throws IOException, InterruptedException {
        tcpClient.init(level, output);
    }
    public void open(InetSocketAddress address) throws Exception {
        tcpClient.open(address);
    }
    public void start() {
        tcpClient.start();
    }
    private void onConnection(TCPClient client, TCPConnection c) {
        if (c.getState() == TCPConnection.State.Connected) {

        }
    }
    private void onRead(TCPClient client, TCPConnection c) throws IOException {
        var buffers = c.getReadBuffers();
        while (buffers.size() > 0) {
            var state = collecter.collect(buffers);
            if (state == Collecter.State.Done) {
                transmission.handleRead(collecter.toByteArray());
                collecter.clear();
            } else if (state == Collecter.State.Error) {
                LogLine.begin(Logger.Level.Error).log("collect error").end();
                collecter.clear();
            }
        }
    }

}
