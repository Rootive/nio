import org.junit.Test;
import org.rootive.nio_rpc.Client;
import org.rootive.rpc.Function;
import org.rootive.rpc.Signature;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;

public class ClientTest {
    @Test
    public void test() throws Exception {
        Client client = new Client();
        client.init();
        client.open(new InetSocketAddress("127.0.0.1", 45555));
        var stub = client.getStub();
        var th = new Thread(() -> {
            try {

                ImaginaryNumber n = new ImaginaryNumber(10, 10);
                Method method1 = ImaginaryNumber.class.getMethod("add", ImaginaryNumber.class, ImaginaryNumber.class);

                Object ret1 = stub
                        .method(method1)
                        .arg(null, n, n)
                        .invoke()
                        .ret(ImaginaryNumber.class);
                System.out.println(ret1);

                Object ret2 = stub
                        .method(method1)
                        .arg(null, stub.sig(new Signature(ImaginaryNumber.class, "x")), n)
                        .invoke()
                        .ret(ImaginaryNumber.class);
                System.out.println(ret2);

                Object ret3 = stub
                        .method(method1)
                        .arg(
                                null,
                                stub.method(method1).arg(null, stub.sig(new Signature(ImaginaryNumber.class, "x")), n),
                                stub.sig(new Signature(ImaginaryNumber.class, "y"))
                        )
                        .invoke()
                        .ret(ImaginaryNumber.class);
                System.out.println(ret3);


            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        th.start();
        client.start();
    }
}
