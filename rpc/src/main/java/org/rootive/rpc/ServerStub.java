package org.rootive.rpc;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.util.HashMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.rootive.gadgets.ByteBufferList;

public class ServerStub {
    @FunctionalInterface public interface Transmission {
        void send(ByteBuffer data);
    }
    static private final ByteBuffer doneGap = Gap.get(Return.Status.Done);
    static private final ByteBuffer transmissionExceptionGap = Gap.get(Return.Status.TransmissionException);
    static private final ByteBuffer parseExceptionGap = Gap.get(Return.Status.ParseException);
    static private final ByteBuffer invocationExceptionGap = Gap.get(Return.Status.InvocationException);
    static private ByteBuffer doneGap() {
        return doneGap.duplicate();
    }
    static private ByteBuffer transmissionExceptionGap() {
        return transmissionExceptionGap.duplicate();
    }
    static private ByteBuffer parseExceptionGap() {
        return parseExceptionGap.duplicate();
    }
    static private ByteBuffer invocationExceptionGap() {
        return invocationExceptionGap.duplicate();
    }

    static public ByteBuffer single(byte[] bs, Type type) {
        return ByteBuffer.wrap(
                        new byte[Constexpr.pre + Constexpr.headerSize + bs.length + Constexpr.post]
                        , Constexpr.pre
                        , Constexpr.headerSize + bs.length
                )
                .putInt(bs.length)
                .put((byte) type.ordinal())
                .put(bs)
                .flip();
    }

    private final ServerStub parent;
    private final HashMap<String, HashMap<String, Object>> map = new HashMap<>();
    private final Transmission transmission;

    public ServerStub(ServerStub parent, Transmission transmission) {
        this.parent = parent;
        this.transmission = transmission;
    }

    public void registerNamespace(String namespaceString, HashMap<String, Object> m) {
        map.put(namespaceString, m);
    }
    public void registerNamespace(Class<?> namespaceString, HashMap<String, Object> m) {
        map.put(Signature.namespaceStringOf(namespaceString), m);
    }
    public void register(String namespaceString, String identifier, Object obj) {
        var namespace = map.get(namespaceString);
        if (namespace != null) {
            namespace.put(identifier, obj);
        } else {
            namespace = new HashMap<>();
            namespace.put(identifier, obj);
            registerNamespace(namespaceString, namespace);
        }
    }
    public void register(Class<?> namespaceString, String identifier, Object obj) {
        register(Signature.namespaceStringOf(namespaceString), identifier, obj);
    }
    public void register(String identifier, Object obj) {
        register(Signature.namespaceStringOf(obj), identifier, obj);
    }
    public void register(String identifier, Function f) {
        register(Signature.namespaceStringOf(f), identifier, f);
    }
    public HashMap<String, Object> getNamespace(String namespaceString) {
        var ret = map.get(namespaceString);
        if (ret == null && parent != null) {
            ret = parent.getNamespace(namespaceString);
            if (ret != null) {
                registerNamespace(namespaceString, ret);
            }
        }
        return ret;
    }
    public Object get(String namespaceString, String identifier) {
        var namespace = getNamespace(namespaceString);
        if (namespace != null) {
            return namespace.get(identifier);
        }
        return null;
    }

    private Object parse(ByteBufferList ds, Class<?> context) throws ParseException, InvocationException {
        var d = ds.removeFirst();
        var t = d.get();
        if (t < 0 || t >= Type.values().length) {
            throw new ParseException("unexpect type: " + t);
        }
        Object ret = null;
        switch (Type.values()[t]) {
            case Signature -> {
                var sig = new String(d.array(), d.arrayOffset() + d.position(), d.arrayOffset() + d.limit());
                var space = sig.indexOf(' ');
                var o = get(sig.substring(0, space), sig.substring(space + 1));
                if (o == null) {
                    throw new ParseException(sig + " refer to null");
                }
                if (o instanceof Function f) {
                    var pcs = f.getParameterClasses();
                    if (ds.count() < pcs.size()) {
                        throw new ParseException("expect " + pcs.size() + " parameters but " + ds.count() + " left");
                    }
                    Object po = parse(ds, pcs.get(0));
                    Object[] ps = new Object[pcs.size() - 1];
                    for (var _i = 0; _i < ps.length; ++_i) {
                        ps[_i] = parse(ds, pcs.get(_i + 1));
                    }
                    try {
                        ret = f.invoke(po, ps);
                    } catch (InvocationTargetException e) {
                        throw new InvocationException(e.getTargetException().getMessage());
                    } catch (IllegalAccessException e) {
                        throw new ParseException(e.getMessage());
                    }
                } else {
                    ret = o;
                }
            }
            case Bytes -> {
                ret = new byte[d.remaining()];
                System.arraycopy(d.array(), d.arrayOffset() + d.position(), (byte[]) ret, 0, d.remaining());
            }
            case Literal -> {
                var s = new String(d.array(), d.arrayOffset() + d.position(), d.remaining());
                try {
                    ret = new ObjectMapper().readValue(s, context);
                } catch (IOException e) {
                    throw new ParseException("parse json: " + s + " failed");
                }
            }
        }
        return ret;
    }

    public void run(Collector c) throws Exception {
        var ds = c.getDone();
        if (ds.head().get() == Type.Signature.ordinal()) {
            try {
                var o = parse(ds, null);
                if (c.getCtx() == Gap.Context.CallOnly) {

                } else if (o instanceof byte[] bs && c.getCtx() == Gap.Context.CallBytes) {
                    res = single(bs, Type.Bytes);
                } else {
                    res = single(new ObjectMapper().writeValueAsBytes(o), Type.Literal);
                }
                gap = doneGap();
            } catch (ParseException | JsonProcessingException e) {
                transmission.send(single(e.getMessage().getBytes(), Type.Bytes));
                transmission.send(parseExceptionGap());
            } catch (InvocationException e) {
                transmission.send(single(e.getMessage().getBytes(), Type.Bytes));
                transmission.send(invocationExceptionGap());
            }
        } else {
            transmission.send(single("expect signature here".getBytes(), Type.Bytes));
            transmission.send(parseExceptionGap());
        }
    }

}
