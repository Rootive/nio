package org.rootive.rpc;

import java.nio.ByteBuffer;

public class Signature implements Actor {
    private final ByteBuffer data;

    public static String namespaceStringOf(Class<?> cls) {
        return cls.getName();
    }
    public static String namespaceStringOf(Object obj) {
        return obj.getClass().getName();
    }
    public static String namespaceStringOf(Function function) {
        return namespaceStringOf(function.getParameterClasses().get(0));
    }

    public Signature(String namespaceString, String identifier) {
        var bs = (namespaceString + ' ' + identifier).getBytes();
        data = ByteBuffer.allocate(bs.length + Constexpr.headerSize)
                .putInt(bs.length)
                .put((byte) Type.Signature.ordinal())
                .put(bs)
                .flip();
    }
    public Signature(Class<?> cls, String identifier) {
        this(namespaceStringOf(cls), identifier);
    }
    public Signature(Object obj, String identifier) {
        this(namespaceStringOf(obj), identifier);
    }
    public Signature(Function function, String identifier) {
        this(namespaceStringOf(function), identifier);
    }

    public ByteBuffer getData() {
        return data.duplicate();
    }

}
