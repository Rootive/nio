package org.rootive.rpc;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;

public class Function {
    private Method method;
    private ArrayList<Class<?>> parameterClasses = new ArrayList<>();
    public Function(Class<?> aClass, Method method) {
        this.method = method;
        parameterClasses.add(aClass);
        parameterClasses.addAll(Arrays.asList(method.getParameterTypes()));
        parameterClasses.trimToSize();
    }
    public ArrayList<Class<?>> getParameterClasses() {
        return parameterClasses;
    }
    public Object invoke(Object obj, Object[] parameters) throws InvocationTargetException, IllegalAccessException {
        return method.invoke(obj, parameters);
    }
    public Method getMethod() {
        return method;
    }
}
