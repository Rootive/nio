package org.rootive.log;

import java.nio.ByteBuffer;

public class LogLine {
    private ByteBuffer buffer;
    private LogLine(Logger.Level level, int size) {
        if (level.compareTo(Logger.getLevel()) >= 0) {
            buffer = ByteBuffer.allocate(size);
        }
    }
    static public LogLine begin(Logger.Level level) {
        return new LogLine(level, 64);
    }
    public LogLine log(String s) {
        if (buffer != null) {
            buffer.put(s.getBytes());
        }
        return this;
    }
    public void end() {
        if (buffer != null) {
            buffer.put("\n".getBytes());
            buffer.limit(buffer.position());
            buffer.position(0);
            Logger.add(buffer);
        }
    }
}
