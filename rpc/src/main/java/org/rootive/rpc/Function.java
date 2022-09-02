package org.rootive.rpc;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class Function {
    private final Method method;
    private final Class<?> objectClass;
    private final Class<?>[] parameterClasses;

    public Function(Class<?> aClass, Method method) {
        this.method = method;
        objectClass = aClass;
        parameterClasses = method.getParameterTypes();
    }
    public Function(Method method) {
        this(method.getDeclaringClass(), method);
    }

    public Class<?> getObjectClass() {
        return objectClass;
    }
    public Class<?>[] getParameterClasses() {
        return parameterClasses;
    }
    public Class<?> getReturnClass() {
        return method.getReturnType();
    }

    public Object invoke(Object obj, Object[] parameters) throws InvocationTargetException, IllegalAccessException {
        return method.invoke(obj, parameters);
    }
    public Functor newFunctor(Signature sig, Object...parameters) throws IOException {
        return new Functor(this, sig, parameters);
    }
}
