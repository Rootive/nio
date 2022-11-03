package org.rootive.nio_rpc;

import org.rootive.nio.EventLoop;
import org.rootive.rpc.server.ServerStub;

import java.util.HashMap;

public class RUDPPeerStub {
    public final RUDPServerStub serverStub;
    public final RUDPClientStub clientStub = new RUDPClientStub();

    public RUDPPeerStub(ServerStub parent, EventLoop eventLoop, HashMap<String, EventLoop> map) {
        serverStub = new RUDPServerStub(parent, eventLoop, map);
    }


}
