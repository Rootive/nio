import org.rootive.gadget.Linked;
import org.rootive.nio.EventLoop;
import org.rootive.nio.RUDPConnection;
import org.rootive.nio.RUDPServer;
import org.rootive.nio_rpc.RUDPPeerStub;
import org.rootive.nio_rpc.RUDPTransmission;
import org.rootive.rpc.Function;
import org.rootive.rpc.Namespace;
import org.rootive.rpc.ServerStub;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public class RUDPPeer {
    @FunctionalInterface public interface Callback {
        void invoke(RUDPConnection c) throws Exception;
    }

    private final RUDPServer server;
    private final ServerStub serverStub = new ServerStub(null);

    private Callback stateCallback;

    public RUDPPeer(EventLoop eventLoop, int timersCount, int threadsCount) {
        server = new RUDPServer(new ScheduledThreadPoolExecutor(timersCount), eventLoop, threadsCount);
        server.setStateCallback(this::onState);
        server.setReadCallback(this::onRead);
        server.setConnectionContext((c) -> new RUDPPeerStub(serverStub, c));
    }

    public void setStateCallback(Callback stateCallback) {
        this.stateCallback = stateCallback;
    }

    public void register(Namespace namespace) {
        serverStub.register(namespace);
    }
    public void register(Function function) {
        serverStub.register(function);
    }
    public void register(String identifier, Object obj) {
        serverStub.register(identifier, obj);
    }
    public void init(InetSocketAddress local) throws Exception {
        server.init(local);
    }
    public RUDPTransmission get(SocketAddress remote) throws Exception {
        return ((RUDPPeerStub) server.get(remote).context).getTransmission();
    }

    private void onState(RUDPConnection c) throws Exception {
        if (c.getState() == RUDPConnection.State.Disconnected) {
            ((RUDPPeerStub) c.context).disconnect();
        }
        if (stateCallback != null) {
            stateCallback.invoke(c);
        }
    }
    private void onRead(RUDPConnection c, Linked<ByteBuffer> l) throws Exception {
        ((RUDPPeerStub) c.context).handleReceived(l);
    }
}
