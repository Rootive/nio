package org.rootive.p2p;

import org.rootive.nio.rudp.RUDPConnection;
import org.rootive.util.Linked;
import org.rootive.nio.EventLoop;
import org.rootive.nio.rudp.RUDPManager;
import org.rootive.nio_rpc.RUDPPeerStub;
import org.rootive.rpc.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.function.BiConsumer;

public class RUDPPeer {
    static public record Context(RUDPPeerStub stub, Object context) { }

    private final RUDPManager manager;
    private final ServerStub serverStub = new ServerStub(null, null);
    private java.util.function.Function<RUDPConnection, Object> contextSetter = (c) -> null;
    private BiConsumer<String, Runnable> dispatcher = (n, r) -> r.run();

    static public ClientStub getClientStub(RUDPConnection c) {
        return ((Context) c.context).stub.getClientStub();
    }

    public RUDPPeer(EventLoop eventLoop, int threadsCount) {
        manager = new RUDPManager(eventLoop, threadsCount);
        manager.setReadCallback(this::onRead);
        manager.setResetCallback(this::onReset);
        manager.setContextSetter((c) -> {
            var stub = new RUDPPeerStub(serverStub, c);
            stub.setDispatcher(dispatcher);
            return new Context(stub, contextSetter.apply(c));
        });
    }

    public ServerStub getServerStub() {
        return serverStub;
    }
    public void setContextSetter(java.util.function.Function<RUDPConnection, Object> contextSetter) {
        this.contextSetter = contextSetter;
    }
    public void setDispatcher(BiConsumer<String, Runnable> dispatcher) {
        this.dispatcher = dispatcher;
    }

    public void register(String identifier, Function function) {
        serverStub.register(identifier, function);
    }
    public void register(String identifier, Object obj) {
        serverStub.register(identifier, obj);
    }

    public void init(InetSocketAddress local) throws IOException, InterruptedException {
        manager.init(local);
    }

    public Return callLiteral(SocketAddress remote, Functor f) {
        var ret = new Return();
        manager.force(remote, (c) -> f.callLiteral(getClientStub(c), ret));
        return ret;
    }
    public Return callBytes(SocketAddress remote, Functor f) {
        var ret = new Return();
        manager.force(remote, (c) -> f.callBytes(getClientStub(c), ret));
        return ret;
    }

    private void onReset(RUDPConnection c) {
        ((Context) c.context).stub.drop();
    }
    private void onRead(RUDPConnection c, Linked<ByteBuffer> l) {
        ((Context) c.context).stub.handleReceived(l);
    }
}
