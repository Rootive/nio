package org.rootive.rpc;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

class ClientInvocationHandler implements InvocationHandler {
    private final ClientStub stub;
    private final Reference obj;
    private final Class<?> aClass;

    ClientInvocationHandler(ClientStub stub, Reference obj, Class<?> aClass) {
        this.stub = stub;
        this.obj = obj;
        this.aClass = aClass;
    }
    @Override
    public Object invoke(Object proxy, Method method, Object[] args)
            throws IOException, SerializationException, ServerRegisterException,
            InvocationException, BadReferenceException, InterruptedException,
            DeserializationException, BadParametersException, UnrecognizedProxyException, NoSuchFieldException, IllegalAccessException {
        return stub.func(new Function(aClass, method)).arg(obj, args).invoke().ret(method.getReturnType());
    }

    Reference getObj() {
        return obj;
    }
}

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
    public Reference sig(Class<?> cls, String identifier) {
        return sig(new Signature(cls, identifier));
    }
    public Reference func(Function function) {
        return sig(new Signature(function));
    }
    public Reference method(Method method) {
        return func(new Function(method.getDeclaringClass(), method));
    }

    public Object proxyOfInterface(Class<?> cls, Reference reference) {
        var handler = new ClientInvocationHandler(this, reference, cls);
        return Proxy.newProxyInstance(cls.getClassLoader(), cls.getInterfaces(), handler);
    }
}
