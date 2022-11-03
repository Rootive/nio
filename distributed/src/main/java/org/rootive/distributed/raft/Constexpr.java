package org.rootive.distributed.raft;

import java.net.InetSocketAddress;

public final class Constexpr {
    static public final InetSocketAddress[] addresses = {
            new InetSocketAddress("127.0.0.1", 45556)
            , new InetSocketAddress("127.0.0.1", 45557)
            , new InetSocketAddress("127.0.0.1", 45558)
//            , new InetSocketAddress("127.0.0.1", 45559)
//            , new InetSocketAddress("127.0.0.1", 45560)
    };


}
