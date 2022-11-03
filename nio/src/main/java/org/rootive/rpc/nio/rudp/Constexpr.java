package org.rootive.rpc.nio.rudp;

public class Constexpr {
    public static final int MTU = 548;
    private static final int localCheckSize = 8;
    private static final int remoteCheckSize = 8;
    static final int checkSize = localCheckSize + remoteCheckSize;
    static final int countSize = 8;
    public static final int headerSize = checkSize + countSize;
}
