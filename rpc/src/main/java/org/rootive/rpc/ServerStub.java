package org.rootive.rpc;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.function.Consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.rootive.util.ByteBufferList;

public class ServerStub {
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
                .mark()
                .putInt(bs.length)
                .put((byte) type.ordinal())
                .put(bs)
                .reset();
    }

    private final ServerStub parent;
    private final HashMap<String, HashMap<String, Object>> map = new HashMap<>();
    private final Consumer<ByteBuffer> transmission;

    public ServerStub(ServerStub parent, Consumer<ByteBuffer> transmission) {
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
                var sig = new String(d.array(), d.arrayOffset() + d.position(), d.remaining());
                var space = sig.indexOf(' ');
                var o = get(sig.substring(0, space), sig.substring(space + 1));
                if (o == null) {
                    throw new ParseException(sig + " refer to null");
                }
                if (o instanceof Function f) {
                    var pcs = f.getParameterClasses();
                    if (ds.count() <= pcs.length) {
                        throw new ParseException("expect more parameters");
                    }
                    Object po = parse(ds, f.getObjectClass());
                    Object[] ps = new Object[pcs.length];
                    for (var _i = 0; _i < ps.length; ++_i) {
                        ps[_i] = parse(ds, pcs[_i]);
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

    public void run(Collector c) {
        var ds = c.getDone();

        ByteBuffer res;
        ByteBuffer gap;
        if (ds.head().get() == Type.Signature.ordinal()) {
            try {
                var o = parse(ds, null);
                if (o instanceof byte[] bs && c.getContext() == Gap.Context.CallBytes) {
                    res = single(bs, Type.Bytes);
                } else {
                    res = single(new ObjectMapper().writeValueAsBytes(o), Type.Literal);
                }
                gap = doneGap();
            } catch (ParseException | JsonProcessingException e) {
                try {
                    res = single(new ObjectMapper().writeValueAsBytes(e.getMessage()), Type.Literal);
                } catch (JsonProcessingException ex) {
                    res = single(new byte[0], Type.Bytes);
                    ex.printStackTrace();
                }
                gap = parseExceptionGap();
            } catch (InvocationException e) {
                try {
                    res = single(new ObjectMapper().writeValueAsBytes(e.getMessage()), Type.Literal);
                } catch (JsonProcessingException ex) {
                    res = single(new byte[0], Type.Bytes);
                    ex.printStackTrace();
                }
                gap = invocationExceptionGap();
            }
        } else {
            try {
                res = single(new ObjectMapper().writeValueAsBytes("expect signature here"), Type.Literal);
            } catch (JsonProcessingException e) {
                res = single(new byte[0], Type.Bytes);
                e.printStackTrace();
            }
            gap = parseExceptionGap();
        }
        transmission.accept(res);
        transmission.accept(gap);
    }

}
