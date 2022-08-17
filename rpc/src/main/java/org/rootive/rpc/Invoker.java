package org.rootive.rpc;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Invoker {
    private final ClientStub stub;
    private final byte[] data;
    private Result result;
    private final ReentrantLock returnLock = new ReentrantLock();
    private final Condition returnCondition = returnLock.newCondition();
    private final Field h;

    public void setReturn(Result result) {
        returnLock.lock();
        this.result = result;
        returnCondition.signal();
        returnLock.unlock();
    }
    private void convert(Object arg, ByteArrayOutputStream outputStream) throws IOException, IllegalAccessException, UnrecognizedProxyException {
        if (arg instanceof Reference) {
            outputStream.write(((Reference) arg).getData());
        } else if (arg instanceof Invoker) {
            var argData = ((Invoker) arg).data;
            outputStream.write(argData, 0, argData.length - 1);
        } else if (arg instanceof Proxy) {
            var handler = h.get(arg);
            if (handler instanceof ClientInvocationHandler) {
                var ref = ((ClientInvocationHandler) handler).getObj();
                outputStream.write(ref.getData());
            } else {
                throw new UnrecognizedProxyException("unrecognized proxy: " + arg);
            }
        } else {
            new ObjectMapper().writeValue(outputStream, arg);
        }
    }
    Invoker(ClientStub stub, Reference reference, Object obj, Object...args) throws IOException, NoSuchFieldException, IllegalAccessException, UnrecognizedProxyException {
        h = Proxy.class.getDeclaredField("h");
        h.setAccessible(true);
        this.stub = stub;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        var referenceData = reference.getData();
        outputStream.write(referenceData, 0, referenceData.length);
        outputStream.write('(');
        convert(obj, outputStream);
        if (args != null) {
            for (Object arg : args) {
                outputStream.write(',');
                convert(arg, outputStream);
            }
        }
        outputStream.write(')');
        outputStream.write(';');
        //BUG Rootive: 一次不必要的拷贝
        data = outputStream.toByteArray();
    }
    public Invoker invoke() {
        stub.getTransmission().toServer(data, this);
        return this;
    }
    public byte[] ret() throws InterruptedException, ServerRegisterException, InvocationException, DeserializationException, SerializationException, BadParametersException, BadReferenceException {
        returnLock.lock();
        while (result == null) {
            returnCondition.await();
        }
        returnLock.unlock();
        var status = result.getStat();
        if (status == Result.Status.BAD_REGISTER) {
            throw new ServerRegisterException(result.getMsg());
        } else if (status == Result.Status.INVOCATION_ERROR) {
            throw new InvocationException(result.getMsg());
        } else if (status == Result.Status.DESERIALIZATION_ERROR) {
            throw new DeserializationException(result.getMsg());
        } else if (status == Result.Status.SERIALIZATION_ERROR) {
            throw new SerializationException(result.getMsg());
        } else if (status == Result.Status.BAD_PARAMETERS) {
            throw new BadParametersException(result.getMsg());
        } else if (status == Result.Status.BAD_REFERENCE) {
            throw new BadReferenceException(result.getMsg());
        } else {
            return result.getData();
        }
    }
    public Object ret(Class<?> returnClass) throws SerializationException, ServerRegisterException, InvocationException, InterruptedException, DeserializationException, IOException, BadReferenceException, BadParametersException {
        return new ObjectMapper().readValue(ret(), returnClass);
    }
    @Override
    public String toString() {
        return new String(data);
    }
}
