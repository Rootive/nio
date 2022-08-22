package org.rootive.nio;

import java.util.HashMap;

public class TCPConnectionPool extends HashMap<String, TCPConnection> {
    public TCPConnection put(TCPConnection v) {
        return put("", v);
    }
    public TCPConnection get() {
        var it = values().iterator();
        return it.hasNext() ? it.next() : null;
    }
    public TCPConnection remove() {
        return remove("");
    }
}
