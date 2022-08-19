package org.rootive.rpc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.rootive.annotation.Reference;
import org.rootive.gadget.ByteBufferList;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;

public class LocalTest {

    static public int method1(int a, String b) {
        System.out.println("method1 " + a + " " + b);
        return 201;
    }
    static public int method2(int a, String b) {
        System.out.println("method2 " + a + " " + b);
        return 401;
    }
    static public String method3() {
        System.out.println("method3");
        return "m3";
    }
    @Test
    public void clientStub() throws ClassNotFoundException, NoSuchMethodException, IOException, NoSuchFieldException, IllegalAccessException {
        ClientStub at = new ClientStub(null);
        var cls = Class.forName("org.rootive.rpc.LocalTest");

        var m1 = cls.getMethod("method1", int.class, String.class);
        Function function1 = new Function(cls, m1);
        Signature f1 = new Signature(function1);

        var m2 = cls.getMethod("method2", int.class, String.class);
        Function function2 = new Function(cls, m2);
        Signature f2 = new Signature(function2);

        var m3 = cls.getMethod("method3");
        Function function3 = new Function(cls, m3);
        Signature f3 = new Signature(function3);

        System.out.println(at.sig(f2).arg(null, at.sig(f1).arg(null, 233, "hi"), "({)}[\"\"]"));
    }

    @Test
    public void parser() throws ClassNotFoundException, NoSuchMethodException, IOException {
        ServerStub stub = new ServerStub(null);
        var cls = Class.forName("org.rootive.rpc.LocalTest");

        var m1 = cls.getMethod("method1", int.class, String.class);
        Function function1 = new Function(cls, m1);
        Signature f1 = new Signature(function1);

        var m2 = cls.getMethod("method2", int.class, String.class);
        Function function2 = new Function(cls, m2);
        Signature f2 = new Signature(function2);

        var m3 = cls.getMethod("method3");
        Function function3 = new Function(cls, m3);
        Signature f3 = new Signature(function3);

        stub.register(f1, function1);
        stub.register(f2, function2);
        stub.register(f3, function3);

        Parser p = new Parser("@.org.rootive.rpc.LocalTest.method2(org.rootive.rpc.LocalTest,int,java.lang.String)(null,@.org.rootive.rpc.LocalTest.method1(org.rootive.rpc.LocalTest,int,java.lang.String)(null,233,\"hi\"),\";({)};[\\\"\\\"]\")");
        System.out.println(p);
        System.out.println(new String(stub.invoke(p)));
    }

    @Test
    public void collecter() {
        byte[] bytes1 = "@.org.rootive.rpc.LocalTest.method2(org.rootiv".getBytes();
        byte[] bytes2 = "e.rpc.LocalTest,int,java.lang.String)(null,@.org.rootive.rpc.LocalTest.method1(org.rootive.r".getBytes();
        byte[] bytes3 = "pc.LocalTest,int,java.lang.String)(null,233,\";hi\"),\";({)};[\\\"\\\"]\");asdasd".getBytes();
        ByteBufferList list = new ByteBufferList();
        list.addLast(ByteBuffer.wrap(bytes1));
        list.addLast(ByteBuffer.wrap(bytes2));
        list.addLast(ByteBuffer.wrap(bytes3));

        Collecter c = new Collecter();
        c.collect(list);
        System.out.println(c);
        assert list.totalRemaining() == 6;
    }

    interface itf {
        void md();
    }
    @Test
    public void invocationHandler() {
        ClientStub stub = new ClientStub(null);
        var res = stub.proxyOfInterface(itf.class, null);
        var res2 = stub.proxyOfInterface(itf.class, null);

        System.out.println(res.getClass());
        // class org.rootive.rpc.$Proxy7

        System.out.println(res.getClass().getSimpleName());
        // $Proxy7

        System.out.println(res2 instanceof Proxy); // true
        System.out.println(res2.getClass().isInterface()); // false


    }

    @Test
    public void proxy() throws NoSuchFieldException {
        Proxy.class.getDeclaredField("h").setAccessible(true);
    }

}
