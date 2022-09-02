package org.rootive.util;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class ByteArrayOutputStreamE extends ByteArrayOutputStream {

    public byte[] getBuf() {
        return buf;
    }
    public void write(ByteBuffer b) {
        write(b.array(), b.arrayOffset() + b.position(), b.arrayOffset() + b.limit());
        b.position(b.limit());
    }
}
