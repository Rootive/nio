import org.rootive.gadget.Linked;
import org.rootive.nio.EventLoop;
import org.rootive.nio.RUDPConnection;
import org.rootive.nio.RUDPServer;
import org.rootive.nio_rpc.RUDPPeerStub;
import org.rootive.nio_rpc.RUDPTransmission;
import org.rootive.rpc.*;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public class RUDPPeer {

    private final RUDPServer server;
    private final ServerStub stub = new ServerStub(null);

    static private final Function connectFunc;
    static private final Signature connectSig;
    static private final Reference connectRef;

    static private final Function punchFunc;
    static private final Signature punchSig;

    static private final Signature peerSig = new Signature(RUDPPeer.class, "peer");
    static private final Reference peerRef = ClientStub.sig(peerSig);

    static {
        Method connectMet = null;
        try {
            connectMet = RUDPPeer.class.getMethod("connect", String.class, int.class);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        assert connectMet != null;
        connectFunc = new Function(RUDPPeer.class, connectMet);
        connectSig = new Signature(connectFunc);
        connectRef = ClientStub.sig(connectSig);

        Method punchMet = null;
        try {
            punchMet = RUDPPeer.class.getMethod("punch", String.class, String.class);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        assert punchMet != null;
        punchFunc = new Function(RUDPPeer.class, punchMet);
        punchSig = new Signature(punchFunc);
    }

    static public RUDPTransmission getTransmission(RUDPConnection c) {
        return ((RUDPPeerStub) c.context).getTransmission();
    }

    public RUDPPeer(EventLoop eventLoop, int timersCount, int threadsCount) {
        server = new RUDPServer(new ScheduledThreadPoolExecutor(timersCount), eventLoop, threadsCount);
        server.setReadCallback(this::onRead);
        server.setDisconnectCallback(this::onDisconnect);
        server.setConnectionContext((c) -> new RUDPPeerStub(stub, c));
    }

    public void register(Namespace namespace) {
        stub.register(namespace);
    }
    public void register(Function function) {
        stub.register(function);
    }
    public void register(String identifier, Object obj) {
        stub.register(identifier, obj);
    }

    public void init(InetSocketAddress local) throws Exception {
        stub.register(peerSig, this);
        stub.register(connectSig, connectFunc);
        stub.register(punchSig, punchFunc);
        server.init(local);
    }

    public void connect(SocketAddress remote) throws Exception {
        server.connect(remote);
    }
    public void connect(String hostname, int port) throws Exception {
        connect(new InetSocketAddress(hostname, port));
    }
    public void invoke(SocketAddress remote, Invoker invoker) throws Exception {
        server.run(remote, (c) -> invoker.invoke(getTransmission(c)));
    }
    public void force(SocketAddress remote, Invoker invoker) throws Exception {
        server.force(remote, (c) -> invoker.invoke(getTransmission(c)));
    }
    public void punch(String aa, int ap, String ba, int bp) throws Exception {
        force(new InetSocketAddress(aa, ap), connectRef.arg(peerRef, ba, bp));
        force(new InetSocketAddress(ba, bp), connectRef.arg(peerRef, aa, ap));
    }
    public void punch(String a, String b) throws Exception {
        int ai = a.indexOf(':');
        int bi = a.indexOf(':');

        punch(
                a.substring(0, ai), Integer.parseInt(a.substring(ai + 1)),
                b.substring(0, bi), Integer.parseInt(b.substring(bi + 1))
        );
    }

    private void onDisconnect(RUDPConnection c) {
        ((RUDPPeerStub) c.context).disconnect();
    }
    private void onRead(RUDPConnection c, Linked<ByteBuffer> l) throws Exception {
        ((RUDPPeerStub) c.context).handleReceived(l);
    }
}
