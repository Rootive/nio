package org.rootive.rpc;

import java.lang.reflect.Method;
import java.sql.Ref;

//BUG Rootive: 需要对Reference统一管理吗？
public class ClientStub {
    private final Transmission transmission;

    public ClientStub(Transmission transmission) {
        this.transmission = transmission;
    }
    Transmission getTransmission() { return transmission; }
    public Reference sig(Signature s) {
        return new Reference(this, s);
    }
    public Reference func(Function function) {
        return sig(new Signature(function));
    }
    public Reference method(Method method) {
        return func(new Function(method.getDeclaringClass(), method));
    }

}
