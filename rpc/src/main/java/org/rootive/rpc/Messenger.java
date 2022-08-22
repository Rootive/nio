package org.rootive.rpc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class Messenger {
    protected final byte[] data;

    Messenger(Reference reference, Object obj, Object...args) throws IOException, IllegalAccessException, NoSuchFieldException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        var referenceData = reference.getData();
        outputStream.write(referenceData, 0, referenceData.length);
        outputStream.write('(');
        ClientStub.convert(obj, outputStream);
        if (args != null) {
            for (Object arg : args) {
                outputStream.write(',');
                ClientStub.convert(arg, outputStream);
            }
        }
        outputStream.write(')');
        outputStream.write(';');
        //BUG Rootive: 一次不必要的拷贝
        data = outputStream.toByteArray();
    }

    @Override
    public String toString() {
        return new String(data);
    }

    public void invoke(MessengerTransmission t) throws Exception {
        t.send(data, this);
    }
}
