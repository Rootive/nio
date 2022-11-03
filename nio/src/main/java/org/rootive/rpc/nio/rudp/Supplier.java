package org.rootive.rpc.nio.rudp;

import java.nio.ByteBuffer;

public interface Supplier {
    ByteBuffer next(long localCheck, long remoteCheck, long count);
    boolean empty();
}
