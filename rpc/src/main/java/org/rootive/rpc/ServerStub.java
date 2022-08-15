package org.rootive.rpc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ServerStub {
    static final private String nullString = "null";

    private final ServerStub parent;
    private final HashMap<String, Namespace> map = new HashMap<>();

    public ServerStub(ServerStub parent) {
        this.parent = parent;
    }
    public void register(Namespace namespace) {
        var namespaceString = namespace.getNamespaceString();
        assert !map.containsKey(namespaceString);
        map.put(namespaceString, namespace);
    }
    public void register(Signature signature, Object obj) {
        var namespace = map.get(signature.getNamespaceString());
        if (namespace != null) {
            namespace.register(signature, obj);
        } else {
            namespace = new Namespace(signature, obj);
            register(namespace);
        }
    }
    public void unregister(Class<?> cls) {
        map.remove(Signature.namespaceStringOf(cls));
    }
    public Namespace get(Class<?> cls) {
        var ret = map.get(Signature.namespaceStringOf(cls));
        if (ret == null && parent != null) {
            ret = parent.get(cls);
            if (ret != null) {
                register(ret);
            }
        }
        return ret;
    }
    public Object get(Signature signature) {
        var namespace = getNamespaceOf(signature);
        if (namespace != null) {
            return namespace.get(signature);
        } else {
            return null;
        }
    }
    public Namespace getNamespaceOf(Signature signature) {
        var ret = map.get(signature.getNamespaceString());
        if (ret == null && parent != null) {
            ret = parent.getNamespaceOf(signature);
            if (ret != null) {
                register(ret);
            }
        }
        return ret;
    }
    public Object invoke(Parser p, Object context) throws JsonProcessingException, InvocationTargetException, IllegalAccessException, BadParametersException, BadReferenceException {
        switch (p.getType()) {
            case Literal -> {
                if (context instanceof Class) {
                    return new ObjectMapper().readValue(p.getLiteral(), (Class<?>) context);
                } else {
                    return null;
                }
            }
            case Reference -> {
                var object = get(p.getSignature());
                if (object == null) {
                    throw new BadReferenceException("unrecognized reference: " + p.getSignature().toString());
                }
                return object;
            }
            case Functor -> {
                Function function = (Function) get(p.getSignature());
                var parameterClasses = function.getParameterClasses();
                var parameterCount = parameterClasses.size();
                var parameterParsers = p.getParameters();
                if (parameterCount == parameterParsers.size()) {
                    Object obj = invoke(parameterParsers.get(0), parameterClasses.get(0));
                    Object[] parameters = new Object[parameterCount - 1];
                    for (var _i = 1; _i < parameterCount; ++_i) {
                        parameters[_i - 1] = invoke(parameterParsers.get(_i), parameterClasses.get(_i));
                    }
                    return function.invoke(obj, parameters);
                } else {
                    throw new BadParametersException("expect " + parameterCount + " parameters but " + parameterParsers.size() + " provided");
                }
            }
            default -> throw new BadParametersException("unrecognized signature: " + p.getLiteral());
        }
    }
    public byte[] invoke(Parser p) throws IOException {
        Result result = new Result();
        ObjectMapper json = new ObjectMapper();
        Object res = null;
        try {
            res = invoke(p, null);
            result.setStat(Result.Status.DONE);
        } catch (JsonProcessingException e) {
            result.setStat(Result.Status.DESERIALIZATION_ERROR);
            result.setMsg(e.getMessage());
        } catch (InvocationTargetException e) {
            result.setStat(Result.Status.INVOCATION_ERROR);
            result.setMsg(e.getTargetException().getMessage());
        } catch (IllegalAccessException e) {
            result.setStat(Result.Status.BAD_REGISTER);
            result.setMsg(e.getMessage());
        } catch (BadReferenceException e) {
            result.setStat(Result.Status.BAD_REFERENCE);
            result.setMsg(e.getMessage());
        } catch (BadParametersException e) {
            result.setStat(Result.Status.BAD_PARAMETERS);
            result.setMsg(e.getMessage());
        }
        if (res != null) {
            try {
                result.setData(json.writeValueAsBytes(res));
            } catch (JsonProcessingException e) {
                result.setStat(Result.Status.SERIALIZATION_ERROR);
                result.setMsg(e.getMessage());
            }
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(128);
        new ObjectMapper().writeValue(outputStream, result);
        outputStream.write(';');
        return outputStream.toByteArray();
    }
}
