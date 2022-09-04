package org.rootive.p2p;

import org.rootive.util.Linked;
import org.rootive.nio.EventLoop;
import org.rootive.nio.RUDPConnection;
import org.rootive.nio.RUDPServer;
import org.rootive.nio_rpc.RUDPPeerStub;
import org.rootive.rpc.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.function.BiConsumer;

public class RUDPPeer {
    static public class Context {
        public RUDPPeerStub stub;
        public Object context;

        public Context(RUDPPeerStub stub, Object context) {
            this.stub = stub;
            this.context = context;
        }
    }

    static private final Signature peerSig = new Signature(RUDPPeer.class, "peer");

    static private Function connectFunc;
    static private Signature connectSig;

    static private Function punchFunc;

    static {
        try {
            connectFunc = new Function(RUDPPeer.class.getMethod("connect", String.class));
            connectSig = new Signature(connectFunc, "connect");

            punchFunc = new Function(RUDPPeer.class.getMethod("punch", String.class, String.class));
        } catch (NoSuchMethodException e) {
            assert false;
        }
    }

    private final RUDPServer server;
    private final ServerStub serverStub = new ServerStub(null, null);
    private java.util.function.Function<RUDPConnection, Object> contextSetter = (c) -> null;

    static public BiConsumer<ByteBuffer, Return> getTransmission(RUDPConnection c) {
        return ((Context) c.context).stub.getTransmission();
    }

    public RUDPPeer(EventLoop eventLoop, int timersCount, int threadsCount) {
        server = new RUDPServer(eventLoop, timersCount, threadsCount);
        server.setReadCallback(this::onRead);
        server.setDisconnectCallback(this::onDisconnect);
        server.setContextSetter((c) -> new Context(new RUDPPeerStub(serverStub, c), contextSetter.apply(c)));

        register("peer", this);
        register("connect", connectFunc);
        register("punch", punchFunc);
    }

    public void setContextSetter(java.util.function.Function<RUDPConnection, Object> contextSetter) {
        this.contextSetter = contextSetter;
    }

    public void register(String identifier, Function function) {
        serverStub.register(identifier, function);
    }
    public void register(String identifier, Object obj) {
        serverStub.register(identifier, obj);
    }

    public void init(InetSocketAddress local) throws IOException, InterruptedException {
        server.init(local);
    }

    public void connect(SocketAddress remote) {
        server.connect(remote);
    }
    public void connect(String address) {
        var g = address.indexOf(':');
        connect(new InetSocketAddress(address.substring(0, g), Integer.parseInt(address.substring(g + 1))));
    }
    public void disconnect(SocketAddress remote) {
        server.run(remote, RUDPConnection::disconnect);
    }

    public Return call(SocketAddress remote, Functor f) {
        var ret = new Return();
        server.run(remote, (c) -> f.callLiteral(getTransmission(c), ret));
        return ret;
    }
    public Return call(String address, Functor f) {
        var g = address.indexOf(':');
        return call(new InetSocketAddress(address.substring(0, g), Integer.parseInt(address.substring(g + 1))), f);
    }
    public Return force(SocketAddress remote, Functor f) {
        var ret = new Return();
        server.force(remote, (c) -> f.callLiteral(getTransmission(c), ret));
        return ret;
    }
    public Return force(String address, Functor f) {
        var g = address.indexOf(':');
        return force(new InetSocketAddress(address.substring(0, g), Integer.parseInt(address.substring(g + 1))), f);
    }

    public void punch(String a, String b) throws IOException {
        call(a, connectFunc.newFunctor(connectSig, peerSig, b));
        call(b, connectFunc.newFunctor(connectSig, peerSig, a));
    }

    private void onDisconnect(RUDPConnection c) {
        ((Context) c.context).stub.drop();
    }
    private void onRead(RUDPConnection c, Linked<ByteBuffer> l) {
        ((Context) c.context).stub.handleReceived(l);
    }
}
