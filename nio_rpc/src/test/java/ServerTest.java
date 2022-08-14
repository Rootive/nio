import org.junit.Test;
import org.rootive.nio_rpc.Server;
import org.rootive.rpc.Namespace;
import org.rootive.rpc.Signature;

import java.net.InetSocketAddress;

public class ServerTest {
    @Test
    public void test() throws Exception {
        Server server = new Server();

        var stub = server.getStub();
        var namespace = new Namespace(ImaginaryNumber.class);
        namespace.autoRegisterFunctions(ImaginaryNumber.class);
        stub.register(namespace);

        ImaginaryNumber x = new ImaginaryNumber(1, 1);
        ImaginaryNumber y = new ImaginaryNumber(2, 2);
        stub.register(new Signature(x.getClass(), "x"), x);
        stub.register(new Signature(y.getClass(), "y"), y);

        server.init(new InetSocketAddress(45555));
        server.start();
    }
}
