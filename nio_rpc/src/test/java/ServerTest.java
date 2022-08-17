import org.rootive.nio_rpc.Server;
import org.rootive.rpc.Namespace;
import org.rootive.rpc.Signature;

import java.net.InetSocketAddress;

public class ServerTest {
    public static void main(String[] args) throws Exception {
        Server server = new Server();
        var stub = server.getStub();

        var namespace = new Namespace(Dog.class);
        namespace.autoRegisterFunctions(Dog.class);
        stub.register(namespace);

        Signature aDogSignature = new Signature(Dog.class, "aDog");
        Signature bDogSignature = new Signature(Dog.class, "bDog");

        stub.register(aDogSignature, new Dog("aDog", 11, new Color(10, 101, 110)));
        stub.register(bDogSignature, new Dog("bDog", 12, new Color(20, 202, 220)));

        server.init(new InetSocketAddress(45555));
        server.start();
    }
}
