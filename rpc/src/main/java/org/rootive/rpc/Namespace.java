package org.rootive.rpc;

import java.util.HashMap;

public class Namespace {
    private final String namespace;
    private final HashMap<String, Object> map = new HashMap<>();

    public Namespace(Signature signature, Object obj) {
        namespace = signature.getNamespaceString();
        register(signature, obj);
    }
    public Namespace(Class<?> cls) {
        namespace = Signature.namespaceStringOf(cls);
    }

    public String getNamespaceString() {
        return namespace;
    }
    public void autoRegisterFunctions(Class<?> cls) {
        var methods = cls.getMethods();
        for (var method : methods) {
            Function function = new Function(cls, method);
            register(new Signature(function), function);
        }
    }
    public void register(Signature signature, Object obj) {
        assert signature.getNamespaceString().equals(namespace);
        var identifier = signature.getIdentifier();
        assert !map.containsKey(identifier);
        map.put(identifier, obj);
    }
    public void unregister(Signature signature) {
        map.remove(signature.getIdentifier());
    }
    public Object get(Signature signature) {
        return map.get(signature.getIdentifier());
    }
}
