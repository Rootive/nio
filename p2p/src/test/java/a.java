import org.rootive.rpc.InvocationException;
import org.rootive.rpc.ParseException;
import org.rootive.rpc.TransmissionException;

import java.io.File;
import java.io.IOException;

public class a {
    public static void main(String[] args) throws TransmissionException, InvocationException, IOException, ParseException, InterruptedException, NoSuchMethodException {
        new RAFTTest().client();
    }
}
