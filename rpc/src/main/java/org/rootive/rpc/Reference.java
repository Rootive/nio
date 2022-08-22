package org.rootive.rpc;

import java.io.IOException;

//BUG Rootive: 检查存在性？
public class Reference {
    private final byte[] data;

    Reference(Signature signature) {
        data = ("@." + signature.toString()).getBytes();
    }

    byte[] getData() { return data; }
    public Invoker arg(Object obj, Object...args) throws IOException, NoSuchFieldException, IllegalAccessException {
        return new Invoker(this, obj, args);
    }
    public Messenger load(Object obj, Object...args) throws IOException, NoSuchFieldException, IllegalAccessException {
        return new Messenger(this, obj, args);
    }
    @Override
    public String toString() {
        return new String(data);
    }
}
