package org.rootive.nio;

import org.junit.Test;

public class LogTest {
    @Test
    public void logTest() {
        try {
            Logger.init(System.out);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        new LogLine(1024).write("log: ").write("test log.").write("this is a double buffering log.").end();
    }
}
