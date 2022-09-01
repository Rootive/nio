package org.rootive.rpc;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;

public class Function {
    private final Method method;
    private final ArrayList<Class<?>> parameterClasses = new ArrayList<>();

    public Function(Class<?> aClass, Method method) {
        this.method = method;
        parameterClasses.add(aClass);
        parameterClasses.addAll(Arrays.asList(method.getParameterTypes()));
        parameterClasses.trimToSize();
    }
    public Function(Method method) {
        this(method.getDeclaringClass(), method);
    }

    public ArrayList<Class<?>> getParameterClasses() {
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
