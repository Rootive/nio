package org.rootive.rpc;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

class ClientInvocationHandler implements InvocationHandler {
    private final InvokerTransmission t;
    private final Reference obj;
    private final Class<?> aClass;

    ClientInvocationHandler(InvokerTransmission t, Reference obj, Class<?> aClass) {
        this.t = t;
        this.obj = obj;
        this.aClass = aClass;
    }
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Exception {
        return ClientStub.func(new Function(aClass, method)).arg(obj, args).invoke(t).ret(method.getReturnType());
    }

    Reference getObj() {
        return obj;
    }
}

public class ClientStub {
    static private Field h;

    static public Reference sig(Signature s) {
        return new Reference(s);
    }
    static public Reference sig(Class<?> cls, String identifier) {
        return sig(new Signature(cls, identifier));
    }
    static public Reference func(Function function) {
        return sig(new Signature(function));
    }
    static public Reference method(Method method) {
        return func(new Function(method.getDeclaringClass(), method));
    }

    static public Object proxyOfInterface(InvokerTransmission t, Class<?> cls, Reference reference) {
        var handler = new ClientInvocationHandler(t, reference, cls);
        return Proxy.newProxyInstance(cls.getClassLoader(), cls.getInterfaces(), handler);
    }
    static public void convert(Object arg, ByteArrayOutputStream outputStream) throws IOException, IllegalAccessException, NoSuchFieldException {
        if (arg instanceof Reference ref) {
            outputStream.write(ref.getData());
        } else if (arg instanceof Invoker invoker) {
            outputStream.write(invoker.data, 0, invoker.data.length - 1);
        } else if (arg instanceof Proxy) {
            if (h == null) {
                h = Proxy.class.getDeclaredField("h");
                h.setAccessible(true);
            }
            var handler = h.get(arg);
            var ref = ((ClientInvocationHandler) handler).getObj();
            outputStream.write(ref.getData());
        } else {
            new ObjectMapper().writeValue(outputStream, arg);
        }
    }
}
