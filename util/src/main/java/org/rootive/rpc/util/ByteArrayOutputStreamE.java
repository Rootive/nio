package org.rootive.rpc.util;

import java.io.ByteArrayOutputStream;

public class ByteArrayOutputStreamE extends ByteArrayOutputStream {

    public byte[] getBuf() {
        return buf;
    }
    public void write(byte[] b, int off, int len) {
        super.write(b, off, len);
    }
}
