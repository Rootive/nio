package org.rootive.rpc;

import java.nio.ByteBuffer;

public class Signature implements Line {


    private final ByteBuffer byteBuffer;

    public static String namespaceStringOf(Class<?> aClass) {
        return aClass.getName();
    }

    public Signature(String namespaceString, String identifier) {
        var bytes = (namespaceString + ' ' + identifier).getBytes();
        byteBuffer = Util.line(Type.Signature, ByteBuffer.wrap(bytes));
    }
    public Signature(Class<?> aClass, String identifier) {
        this(namespaceStringOf(aClass), identifier);
    }

    @Override
    public ByteBuffer toByteBuffer() {
        return byteBuffer.duplicate();
    }
}
