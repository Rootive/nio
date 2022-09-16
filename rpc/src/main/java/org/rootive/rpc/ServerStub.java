package org.rootive.rpc;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.rootive.util.LinkedByteBuffer;

public class ServerStub {
    static private record Entry(Object object, boolean bProtected) { }

    private final ServerStub parent;
    private final HashMap<String, HashMap<String, Entry>> map = new HashMap<>();
    private final Consumer<ByteBuffer> transmission;
    private BiConsumer<String, Runnable> dispatcher = (n, r) -> r.run();

    public ServerStub(ServerStub parent, Consumer<ByteBuffer> transmission) {
        this.parent = parent;
        this.transmission = transmission;
    }
    public void setDispatcher(BiConsumer<String, Runnable> dispatcher) {
        this.dispatcher = dispatcher;
    }

    public void registerNamespace(String namespaceString, HashMap<String, Entry> m) {
        map.put(namespaceString, m);
    }
    public void registerNamespace(Class<?> namespaceString, HashMap<String, Entry> m) {
        map.put(Signature.namespaceStringOf(namespaceString), m);
    }
    public void register(String namespaceString, String identifier, Object object, boolean bProtected) {
        var namespace = map.get(namespaceString);
        if (namespace != null) {
            namespace.put(identifier, new Entry(object, bProtected));
        } else {
            namespace = new HashMap<>();
            namespace.put(identifier, new Entry(object, bProtected));
            registerNamespace(namespaceString, namespace);
        }
    }
    public void register(Class<?> namespaceString, String identifier, Object object) {
        register(Signature.namespaceStringOf(namespaceString), identifier, object, true);
    }
    public void register(String identifier, Object object) {
        register(Signature.namespaceStringOf(object), identifier, object, true);
    }
    public void register(String identifier, Function f) {
        register(Signature.namespaceStringOf(f), identifier, f, true);
    }

    private HashMap<String, Entry> getNamespace(String namespaceString) {
        var ret = map.get(namespaceString);
        if (ret == null && parent != null) {
            ret = parent.getNamespace(namespaceString);
            if (ret != null) {
                registerNamespace(namespaceString, ret);
            }
        }
        return ret;
    }
    private Entry get(String namespaceString, String identifier) {
        var namespace = getNamespace(namespaceString);
        if (namespace != null) {
            return namespace.get(identifier);
        }
        return null;
    }

    private Object parse(LinkedByteBuffer ds, Class<?> expect, String context) throws ParseException, InvocationException {
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
                var namespace = sig.substring(0, space);
                var entry = get(namespace, sig.substring(space + 1));
                if (entry == null) {
                    throw new ParseException(sig + " refer to null");
                }
                if (entry.bProtected && !namespace.equals(context)) {
                    throw new ParseException(sig + " reject");
                }
                if (entry.object instanceof Function f) {
                    var pcs = f.getParameterClasses();
                    if (ds.count() <= pcs.length) {
                        throw new ParseException("expect more parameters");
                    }
                    Object po = parse(ds, f.getObjectClass(), context);
                    Object[] ps = new Object[pcs.length];
                    for (var _i = 0; _i < ps.length; ++_i) {
                        var _h = ds.head();
                        ps[_i] = parse(ds, pcs[_i], context);
                    }
                    try {
                        ret = f.invoke(po, ps);
                    } catch (InvocationTargetException e) {
                        throw new InvocationException(e.getTargetException().getMessage());
                    } catch (IllegalAccessException e) {
                        throw new ParseException(e.getMessage());
                    }
                } else {
                    ret = entry.object;
                }
            }
            case Bytes -> {
                ret = new byte[d.remaining()];
                System.arraycopy(d.array(), d.arrayOffset() + d.position(), (byte[]) ret, 0, d.remaining());
            }
            case Literal -> {
                var s = new String(d.array(), d.arrayOffset() + d.position(), d.remaining());
                try {
                    ret = new ObjectMapper().readValue(s, expect);
                } catch (IOException e) {
                    throw new ParseException("parse json: " + s + " failed");
                }
            }
        }
        return ret;
    }
    public void handleSignature(Collector c, String namespace) {
        ByteBuffer res = null, gap;
        try {
            var o = parse(c.getDone(), null, namespace);
            if (o instanceof byte[] bs && c.getContext() == Gap.Context.CallBytes) {
                res = Util.single(bs, Type.Bytes);
            } else {
                res = Util.single(new ObjectMapper().writeValueAsBytes(o), Type.Literal);
            }
            gap = Gap.get(Gap.Context.Return, c.getCheck());
        } catch (ParseException | JsonProcessingException e) {

            try { res = Util.single(new ObjectMapper().writeValueAsBytes(e.getMessage()), Type.Literal); }
            catch (JsonProcessingException ex) { assert false; }
            gap = Gap.get(Gap.Context.ParseException, c.getCheck());

        } catch (InvocationException e) {

            try { res = Util.single(new ObjectMapper().writeValueAsBytes(e.getMessage()), Type.Literal); }
            catch (JsonProcessingException ex) { assert false; }
            gap = Gap.get(Gap.Context.InvocationException, c.getCheck());

        }
        if (transmission != null) {
            transmission.accept(res);
            transmission.accept(gap);
        }
    }
    public void handleReceived(Collector c) {
        var d = c.getDone().head();
        if (d.get() == Type.Signature.ordinal()) {
            var sig = new String(d.array(), d.arrayOffset() + d.position(), d.remaining());
            var namespace = sig.substring(0, sig.indexOf(' '));
            dispatcher.accept(namespace, () -> handleSignature(c, namespace));
        } else {

            ByteBuffer res = null, gap;
            try { res = Util.single(new ObjectMapper().writeValueAsBytes("expect signature here"), Type.Literal); }
            catch (JsonProcessingException e) { assert false; }
            gap = Gap.get(Gap.Context.ParseException, c.getCheck());
            if (transmission != null) {
                transmission.accept(res);
                transmission.accept(gap);
            }

        }
    }

}
