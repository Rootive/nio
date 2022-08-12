package org.rootive.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class Acceptor {
    @FunctionalInterface
    public interface Callback {
        void accept(SocketChannel sc) throws Exception;
    }

    private ServerSocketChannel channel;
    private SelectionKey selectionKey;
    private Callback newConnectionCallback;

    public void bind(InetSocketAddress local, Callback newConnectionCallback) throws IOException {
        this.newConnectionCallback = newConnectionCallback;
        channel = ServerSocketChannel.open();
        channel.bind(local);
    }
    public void register(EventLoop eventLoop) throws IOException {
        channel.configureBlocking(false);
        selectionKey = eventLoop.add(channel, (sk) -> handleEvent(), SelectionKey.OP_ACCEPT);
    }
    public void handleEvent() throws Exception {
        newConnectionCallback.accept(channel.accept());
    }
}
