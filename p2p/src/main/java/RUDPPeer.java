import org.rootive.util.Linked;
import org.rootive.nio.EventLoop;
import org.rootive.nio.RUDPConnection;
import org.rootive.nio.RUDPServer;
import org.rootive.nio_rpc.RUDPPeerStub;
import org.rootive.nio_rpc.RUDPTransmission;
import org.rootive.rpc.*;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.ScheduledThreadPoolExecutor;
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

    private final RUDPServer server;
    private final ServerStub stub = new ServerStub(null, null);
    private java.util.function.Function<RUDPConnection, Object> contextSetter = (c) -> null;

    static public BiConsumer<ByteBuffer, Functor> getTransmission(RUDPConnection c) {
        return ((Context) c.context).stub.getTransmission();
    }
    public void setContextSetter(java.util.function.Function<RUDPConnection, Object> contextSetter) {
        this.contextSetter = contextSetter;
    }

    public RUDPPeer(EventLoop eventLoop, int timersCount, int threadsCount) {
        server = new RUDPServer(new ScheduledThreadPoolExecutor(timersCount), eventLoop, threadsCount);
        server.setReadCallback(this::onRead);
        server.setDisconnectCallback(this::onDisconnect);
        server.setContextSetter((c) -> new Context(new RUDPPeerStub(stub, c), contextSetter.apply(c)));
    }

    public void register(String identifier, Function function) {
        stub.register(identifier, function);
    }
    public void register(String identifier, Object obj) {
        stub.register(identifier, obj);
    }

    public void init(InetSocketAddress local) throws IOException, InterruptedException {

        server.init(local);
    }

    public void connect(SocketAddress remote) {
        server.connect(remote);
    }
    public void connect(String hostname, int port) {
        connect(new InetSocketAddress(hostname, port));
    }
    public void invoke(SocketAddress remote, Functor f) {
        server.run(remote, (c) -> f.callLiteral(getTransmission(c)));
    }
    public void force(SocketAddress remote, Functor f) {
        server.force(remote, (c) -> f.callLiteral(getTransmission(c)));
    }
    public void disconnect(SocketAddress remote) {
        server.run(remote, RUDPConnection::disconnect);
    }

    private void onDisconnect(RUDPConnection c) {
        ((Context) c.context).stub.disconnect();
    }
    private void onRead(RUDPConnection c, Linked<ByteBuffer> l) {
        ((Context) c.context).stub.handleReceived(l);
    }
}
