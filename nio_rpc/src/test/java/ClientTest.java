import org.rootive.nio_rpc.Client;
import org.rootive.rpc.Reference;
import org.rootive.rpc.Signature;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;

public class ClientTest {
    public static void main(String[] args) throws Exception {
        Client client = new Client();
        client.init();
        client.open(new InetSocketAddress("127.0.0.1", 45555));
        var th = new Thread(() -> {
            try {

                var stub = client.getStub();

                Signature aDogSignature = new Signature(Dog.class, "aDog");
                Reference aDogReference = stub.sig(aDogSignature);
                DogInterface aDogIf = (DogInterface) stub.proxyOfInterface(Dog.class, aDogReference);

                Reference bDogReference = stub.sig(Dog.class, "bDog");
                DogInterface bDogIf = (DogInterface) stub.proxyOfInterface(Dog.class, bDogReference);

                boolean b;
                Dog aDog = aDogIf.info(); System.out.println(aDog); // {"name":"aDog","age":11,"color":{"r":10,"g":101,"b":110}}
                b = bDogIf.isTheSameAgeWith(aDog); System.out.println(b); // false

                b = aDogIf.isTheSameAgeWith(bDogIf); System.out.println(b);


                b = aDogIf.isTheSameAgeWith(bDogIf.fork("cDog")); System.out.println(b);

                Method isTheSameAgeWith = Dog.class.getMethod("isTheSameAgeWith", Dog.class);
                Method fork = Dog.class.getMethod("fork", String.class);
                b = (boolean) stub.method(isTheSameAgeWith).arg(
                        aDogReference,
                        stub.method(fork).arg(bDogReference, "cDog")
                ).invoke().ret(boolean.class); System.out.println(b);

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        th.start();
        client.start();
    }
}
