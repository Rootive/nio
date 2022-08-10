package org.rootive.rpc;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ServerStub {
    static final private String nullString = "null";

    private final ServerStub parent;
    private final HashMap<String, Namespace> map = new HashMap<>();
    private final ObjectMapper json = new ObjectMapper();

    public ServerStub(ServerStub parent) {
        this.parent = parent;
    }
    public void register(Namespace namespace) {
        var namespaceString = namespace.toString();
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
    private Object get(String parameterString, Class<?> parameterClass) throws JsonProcessingException {
        if (parameterString.length() > 2 && parameterString.charAt(0) == '@' && parameterString.charAt(1) == '.') {
            var sub = parameterString.substring(2);
            if (sub.equals(nullString)) {
                return null;
            } else {
                Signature signature = new Signature(sub);
                return get(signature);
            }
        }
        else {
            return json.readValue(parameterString, parameterClass);
        }
    }
    public Object invoke(Parser p) throws JsonProcessingException, InvocationTargetException, IllegalAccessException {
        Function function = (Function) get(p.getSignature());
        var parameterStrings = p.getParameterStrings();
        var parameterClasses = function.getParameterClasses();
        var parameterCount = parameterStrings.size();
        Object obj = get(parameterStrings.get(0), parameterClasses.get(0));
        Object[] parameters = new Object[parameterCount - 1];
        if (parameterCount != parameterClasses.size()) {  } //BUG Rootive
        for (var _i = 1; _i < parameterCount; ++_i) {
            parameters[_i - 1] = get(parameterStrings.get(_i), parameterClasses.get(_i));
        }
        return function.invoke(obj, parameters);
    }
}
