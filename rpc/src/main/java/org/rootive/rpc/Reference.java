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
    public Invoker argumentsAre(Object...args) throws IOException {
        return new Invoker(stub, this, args);
    }
    public Invoker arg(Object...args) throws IOException {
        return argumentsAre(args);
    }
    @Override
    public String toString() {
        return new String(data);
    }
}
