package org.rootive.rpc.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.rootive.rpc.*;
import org.rootive.rpc.util.LinkedByteBuffer;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public abstract class ServerStub {
    private final ServerStub parent;
    private final HashMap<String, HashMap<String, Object>> map = new HashMap<>();

    public ServerStub(ServerStub parent) {
        this.parent = parent;
    }

    public void registerNamespace(String namespaceString, HashMap<String, Object> m) {
        map.put(namespaceString, m);
    }
    public void registerNamespace(Class<?> namespaceString, HashMap<String, Object> m) {
        map.put(Signature.namespaceStringOf(namespaceString), m);
    }
    public void register(String namespaceString, String identifier, Object object) {
        var namespace = map.get(namespaceString);
        if (namespace != null) {
            namespace.put(identifier, object);
        } else {
            namespace = new HashMap<>();
            namespace.put(identifier, object);
            registerNamespace(namespaceString, namespace);
        }
    }
    public void register(Class<?> namespaceString, String identifier, Object object) {
        register(Signature.namespaceStringOf(namespaceString), identifier, object);
    }

    private HashMap<String, Object> getNamespace(String namespaceString) {
        var ret = map.get(namespaceString);
        if (ret == null && parent != null) {
            ret = parent.getNamespace(namespaceString);
            if (ret != null) {
                registerNamespace(namespaceString, ret);
            }
        }
        return ret;
    }

    protected Object get(String namespaceString, String identifier) {
        var namespace = getNamespace(namespaceString);
        if (namespace != null) {
            return namespace.get(identifier);
        }
        return null;
    }

    protected abstract void send(Object with, ByteBuffer[] byteBuffer);
    protected abstract void put(String identifier, Object object);
    protected abstract void dispatch(String string, Runnable runnable);

    public void handle(Object with, List<Collector> collectors) {
        var back = collectors.get(collectors.size() - 1);
        switch (back.getGap().getStruct()) {
            case Gap.SINGLE -> handle(back, (Line line, Gap gap) -> {
                gap.setStruct(Gap.SINGLE);
                sendLine(with, line, gap);
            });
            case Gap.BULK -> {
                var count = new AtomicInteger();
                for (var collector : collectors) {
                    handle(collector, (Line line, Gap gap) -> {
                        if (count.incrementAndGet() == collectors.size()) {
                            gap.setStruct(Gap.BULK);
                            sendLine(with, line, gap);
                        } else {
                            gap.setStruct(Gap.CONTINUE);
                            sendLine(with, line, gap);
                        }
                    });
                }
            }
            case Gap.CHAIN -> handleChain(with, collectors, 0);
        }
    }

    private void handleChain(Object with, List<Collector> collectors, int index) {
        handle(collectors.get(index), (Line line, Gap gap) -> {
            int next = index + 1;
            int back = collectors.size() - 1;
            if (gap.getException() == 0) {
                gap.setStruct(index == back ? Gap.CHAIN : Gap.CONTINUE);
                sendLine(with, line, gap);
                if (next <= back) {
                    handleChain(with, collectors, next);
                }
            } else {
                gap.setStruct(index == back ? Gap.CHAIN : Gap.CONTINUE);
                sendLine(with, line, gap);
                for (int i = next; i <= back; ++i) {
                    var replyLine = new Bytes(ByteBuffer.wrap("chain failed".getBytes()));
                    var replyGap = new Gap((byte) (Gap.RETURN | Gap.INVOCATION_EXCEPTION | (i == back ? Gap.CHAIN : Gap.CONTINUE)), collectors.get(index).getGap().token);
                    sendLine(with, replyLine, replyGap);
                }
            }
        });
    }

    private record Entry(String namespace, Method method, Object object, Object[] parameters) { }
    private void handle(Collector collector, BiConsumer<Line, Gap> callback) {
        var gap = collector.getGap();
        var done = collector.getDone();
        var entry = parse(done, gap, callback);
        if (entry != null) {
            switch (gap.getAction()) {
                case Gap.LITERAL -> handleLiteral(entry, gap, callback);
                case Gap.BYTES -> handleBytes(entry, gap, callback);
                case Gap.KEEP -> handleKeep(entry, gap, callback);
            }
        }
    }
    private void sendLine(Object with, Line line, Gap gap) {
        var reply = new ByteBuffer[2];
        reply[0] = line.toByteBuffer();
        reply[1] = gap.line();
        send(with, reply);
    }
    private Entry parseEntry(LinkedByteBuffer byteBuffers, Gap gap, BiConsumer<Line, Gap> callback) {
        Consumer<String> exception = (String message) -> callback.accept(new Bytes(ByteBuffer.wrap(message.getBytes())), new Gap((byte) (Gap.RETURN | Gap.PARSE_EXCEPTION), gap.token));

        if (byteBuffers.count() < 2) {
            exception.accept("expect 2 or more lines here");
            return null;
        }
        Method method = null;
        String methodNamespace = null;
        var byteBuffer = byteBuffers.removeFirst();
        if (byteBuffer.get() == Type.Signature.ordinal()) {
            var signatureString = new String(byteBuffer.array(), byteBuffer.arrayOffset() + byteBuffer.position(), byteBuffer.remaining());
            var blank = signatureString.indexOf(' ');
            methodNamespace = signatureString.substring(0, blank);
            var identifier = signatureString.substring(blank + 1);
            if (get(methodNamespace, identifier) instanceof Method object) {
                method = object;
            }
        }
        if (method == null) {
            exception.accept("expect method signature here");
            return null;
        }
        Object object = null;
        byteBuffer = byteBuffers.removeFirst();
        if (byteBuffer.get() == Type.Signature.ordinal()) {
            var signatureString = new String(byteBuffer.array(), byteBuffer.arrayOffset() + byteBuffer.position(), byteBuffer.remaining());
            var blank = signatureString.indexOf(' ');
            var namespace = signatureString.substring(0, blank);
            var identifier = signatureString.substring(blank + 1);
            object = get(namespace, identifier);
        }
        if (object == null) {
            exception.accept("expect object signature here");
            return null;
        }

        var parameterClasses = method.getParameterTypes();
        if (byteBuffers.count() < parameterClasses.length) {
            exception.accept("expect " + parameterClasses.length + " parameters here");
            return null;
        }
        return new Entry(methodNamespace, method, object, new Object[parameterClasses.length]);
    }
    private Entry parse(LinkedByteBuffer byteBuffers, Gap gap, BiConsumer<Line, Gap> callback) {
        Consumer<String> exception = (String message) -> callback.accept(new Bytes(ByteBuffer.wrap(message.getBytes())), new Gap((byte) (Gap.RETURN | Gap.PARSE_EXCEPTION), gap.token));

        var entry = parseEntry(byteBuffers, gap, callback);
        if (entry == null) return null;
        var parameterClasses = entry.method.getParameterTypes();
        for (int index = 0; index < parameterClasses.length; ++index) {
            var byteBuffer = byteBuffers.removeFirst();
            var type = byteBuffer.get();
            if (type < 0 || type >= Type.values().length) {
                exception.accept("illegal type " + type);
                return null;
            }
            switch (Type.values()[type]) {
                case Literal -> {
                    try {
                        entry.parameters[index] = new ObjectMapper().readValue(byteBuffer.array(), byteBuffer.arrayOffset() + byteBuffer.position(), byteBuffer.remaining(), parameterClasses[index]);
                    } catch (IOException e) {
                        exception.accept(e.getMessage());
                        return null;
                    }
                }
                case Bytes -> {
                    if (parameterClasses[index] != byte[].class) {
                        exception.accept("unexpect byte[] here");
                        return null;
                    }
                    var bytes = new byte[byteBuffer.remaining()];
                    System.arraycopy(byteBuffer.array(), byteBuffer.arrayOffset() + byteBuffer.position(), bytes, 0, bytes.length);
                    entry.parameters[index] = bytes;
                }
                case Signature -> {
                    var signatureString = new String(byteBuffer.array(), byteBuffer.arrayOffset() + byteBuffer.position(), byteBuffer.remaining());
                    var blank = signatureString.indexOf(' ');
                    var namespace = signatureString.substring(0, blank);
                    var identifier = signatureString.substring(blank + 1);
                    entry.parameters[index] = get(namespace, identifier);
                    if (entry.parameters[index] == null) {
                        exception.accept("no such signature here");
                        return null;
                    }
                }
            }
        }
        return entry;
    }
    private void handleLiteral(Entry entry, Gap gap, BiConsumer<Line, Gap> callback) {
        dispatch(entry.namespace(), ()-> {
            Line line;
            Gap reply;
            try {
                line = new Literal(entry.method().invoke(entry.object(), entry.parameters));
                reply = new Gap(Gap.RETURN, gap.token);
            } catch (JsonProcessingException e) {
                line = new Bytes(ByteBuffer.wrap(e.getMessage().getBytes()));
                reply = new Gap((byte) (Gap.RETURN | Gap.PARSE_EXCEPTION), gap.token);
            } catch (IllegalAccessException | InvocationTargetException e) {
                line = new Bytes(ByteBuffer.wrap(e.getMessage().getBytes()));
                reply = new Gap((byte) (Gap.RETURN | Gap.INVOCATION_EXCEPTION), gap.token);
            }
            callback.accept(line, reply);
        });
    }
    private void handleBytes(Entry entry, Gap gap, BiConsumer<Line, Gap> callback) {
        dispatch(entry.namespace(), ()-> {
            Line line;
            Gap reply;
            if (entry.method.getReturnType() == byte[].class) {
                try {
                    line = new Bytes(ByteBuffer.wrap((byte[]) entry.method().invoke(entry.object(), entry.parameters)));
                    reply = new Gap(Gap.RETURN, gap.token);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    line = new Bytes(ByteBuffer.wrap(e.getMessage().getBytes()));
                    reply = new Gap((byte) (Gap.RETURN | Gap.INVOCATION_EXCEPTION), gap.token);
                }
            } else {
                line = new Bytes(ByteBuffer.wrap("unexpect return byte[] here".getBytes()));
                reply = new Gap((byte) (Gap.RETURN | Gap.PARSE_EXCEPTION), gap.token);
            }
            callback.accept(line, reply);
        });
    }
    private void handleKeep(Entry entry, Gap gap, BiConsumer<Line, Gap> callback) {
        dispatch(entry.namespace(), ()-> {
            Line line = null;
            Gap reply;
            try {
                put(gap.identifier, entry.method().invoke(entry.object(), entry.parameters()));
                try {
                    line = new Literal(true);
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                }
                reply = new Gap(Gap.RETURN, gap.token);
            } catch (IllegalAccessException | InvocationTargetException e) {
                put(gap.identifier, e.getMessage());
                try {
                    line = new Literal(false);
                } catch (JsonProcessingException exception) {
                    exception.printStackTrace();
                }
                reply = new Gap(Gap.RETURN, gap.token);
            }
            callback.accept(line, reply);
        });
    }
}
