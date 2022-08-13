package org.rootive.rpc;

import java.util.stream.Collectors;

public class Signature {
    private final String namespaceString;
    private final String identifier;

    public static String namespaceStringOf(Object obj) {
        return obj.getClass().getName();
    }
    public static String namespaceStringOf(Class<?> cls) {
        return cls.getName();
    }
    public static String namespaceStringOf(Function function) {
        return function.getParameterClasses().get(0).getName();
    }
    public static String identifierOf(Function function) {
        return function.getMethod().getName() +
                '(' + function.getParameterClasses().stream().
                map(Class::getName).
                collect(Collectors.joining(",")) + ')';
    }
    public Signature(String string) {
        var res = string.indexOf('(');
        int lbrac;
        if (res == -1) {
            lbrac = string.length();
        } else {
            lbrac = res;
        }
        var point = string.lastIndexOf('.', lbrac);
        namespaceString = string.substring(0, point);
        identifier = string.substring(point + 1);
    }
    public Signature(Function function) {
        namespaceString = namespaceStringOf(function);
        identifier = identifierOf(function);
    }
    public Signature(Class<?> cls, String identifier) {
        namespaceString = namespaceStringOf(cls);
        this.identifier = identifier;
    }
    @Override
    public String toString() {
        return namespaceString + '.' + identifier;
    }

    @Override
    public boolean equals(Object obj) {
        return (obj instanceof Signature) && ((Signature) obj).namespaceString.equals(namespaceString) && ((Signature) obj).identifier.equals(identifier);
    }
    public String getNamespaceString() {
        return namespaceString;
    }
    public String getIdentifier() {
        return identifier;
    }
}
