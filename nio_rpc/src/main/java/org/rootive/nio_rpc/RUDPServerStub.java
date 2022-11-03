package org.rootive.nio_rpc;

import org.rootive.nio.EventLoop;
import org.rootive.nio.rudp.RUDPConnection;
import org.rootive.nio.rudp.RUDPPieces;
import org.rootive.rpc.Collector;
import org.rootive.rpc.server.ServerStub;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class RUDPServerStub extends ServerStub {
    private final EventLoop eventLoop;
    private final HashMap<String, EventLoop> map;
    private ArrayList<Collector> collectors = new ArrayList<>();
    static private Method getLocal;
    static {
        try {
            getLocal = RUDPServerStub.class.getMethod("getLocal", String.class);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    public RUDPServerStub() {
        super(null);
        this.eventLoop = null;
        this.map = null;
    }

    public RUDPServerStub(ServerStub parent, EventLoop eventLoop, HashMap<String, EventLoop> map) {
        super(parent);
        this.eventLoop = eventLoop;
        this.map = map;
        register("local", "get", getLocal);
        register("local", "object", this);
    }
    public Object getLocal(String identifier) {
        System.out.println("getLocal");
        return get("local", identifier);
    }

    @Override
    protected void send(Object with, ByteBuffer[] byteBuffer) {
        ((RUDPConnection) with).message(new RUDPPieces(byteBuffer));
    }

    @Override
    protected void put(String identifier, Object object) {
        eventLoop.run(() -> register("local", identifier, object));
    }

    @Override
    protected void dispatch(String string, Runnable runnable) {
        if (string.equals("local")) {
            eventLoop.run(runnable);
        }
        final EventLoop eventLoop = map.get(string);
        if (eventLoop != null) {
            eventLoop.run(runnable);
        }
    }

    public List<Collector> collect(ByteBuffer byteBuffer) {
        if (collectors.isEmpty()) {
            collectors.add(new Collector());
        }
        while (byteBuffer.remaining() > 0) {
            var back = collectors.get(collectors.size() - 1);
            if (back.collect(byteBuffer)) {
                if (!back.getGap().isContinue()) {
                    var ret = collectors;
                    collectors = new ArrayList<>();
                    return ret;
                }
                collectors.add(new Collector());
            }

        }
        return null;
    }
}
