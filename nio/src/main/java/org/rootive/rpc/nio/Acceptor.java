package org.rootive.rpc.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.function.Consumer;
//
public class Acceptor implements Handler {
    private ServerSocketChannel channel;
    private SelectionKey selectionKey;
    private Consumer<SocketChannel> newConnectionCallback;

    public void bind(InetSocketAddress local, Consumer<SocketChannel> newConnectionCallback) throws IOException {
        this.newConnectionCallback = newConnectionCallback;
        channel = ServerSocketChannel.open();
        channel.bind(local);
    }
    public void register(EventLoop eventLoop) throws IOException {
        channel.configureBlocking(false);
        selectionKey = eventLoop.add(channel, SelectionKey.OP_ACCEPT, this);
    }
    @Override
    public void handleEvent() {
        try {
            newConnectionCallback.accept(channel.accept());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
