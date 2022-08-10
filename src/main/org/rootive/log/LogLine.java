package org.rootive.log;

import java.nio.ByteBuffer;

public class LogLine {
    private ByteBuffer buffer;
    public LogLine(int size) {
        restart(size);
    }
    public LogLine restart(int size) {
        buffer = ByteBuffer.allocate(size);
        return this;
    }
    public LogLine write(String s) {
        buffer.put(s.getBytes());
        return this;
    }
    public void end() {
        buffer.limit(buffer.position());
        buffer.position(0);
        Logger.add(buffer);
    }
}
