package org.rootive.rpc;

import java.io.IOException;

//BUG Rootive: 检查存在性？
public class Reference {
    private final byte[] data;
    private final ClientStub stub;

    Reference(ClientStub stub, Signature signature) {
        this.stub = stub;
        data = ("@." + signature.toString()).getBytes();
    }

    byte[] getData() { return data; }
    public Invoker arg(Object obj, Object...args) throws IOException, NoSuchFieldException, IllegalAccessException {
        return new Invoker(stub, this, obj, args);
    }
    @Override
    public String toString() {
        return new String(data);
    }
    ClientStub getStub() {
        return stub;
    }
}
