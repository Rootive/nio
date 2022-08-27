package org.rootive.rpc;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Invoker {
    private Result result;
    private final ReentrantLock returnLock = new ReentrantLock();
    private final Condition returnCondition = returnLock.newCondition();
    protected final byte[] data;

    Invoker(Reference reference, Object obj, Object...args) throws IOException, NoSuchFieldException, IllegalAccessException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        for (var _i = 0; _i < ClientStub.headerSize; ++_i) {
            outputStream.write(0);
        }
        var referenceData = reference.getData();
        outputStream.write(referenceData, 0, referenceData.length);
        outputStream.write('(');
        ClientStub.convert(obj, outputStream);
        if (args != null) {
            for (Object arg : args) {
                outputStream.write(',');
                ClientStub.convert(arg, outputStream);
            }
        }
        outputStream.write(')');
        outputStream.write(';');
        //BUG Rootive: 一次不必要的拷贝
        data = outputStream.toByteArray();
    }

    @Override
    public String toString() {
        return new String(data);
    }

    public Invoker invoke(Transmission t) throws Exception {
        t.send(data, this);
        return this;
    }
    public void setReturn(Result result) {
        returnLock.lock();
        this.result = result;
        returnCondition.signal();
        returnLock.unlock();
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
        } else if (status == Result.Status.BAD_TRANSMISSION) {
            throw new BadReferenceException(result.getMsg());
        } else {
            return result.getData();
        }
    }
    public <T> T ret(Class<T> returnClass) throws SerializationException, ServerRegisterException, InvocationException, BadReferenceException, InterruptedException, DeserializationException, BadParametersException, IOException {
        return new ObjectMapper().readValue(ret(), returnClass);
    }
    public boolean isReturned() {
        boolean ret;
        returnLock.lock();
        ret = result != null;
        returnLock.unlock();
        return ret;
    }
}
