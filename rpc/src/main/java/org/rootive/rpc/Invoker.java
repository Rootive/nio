package org.rootive.rpc;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Ref;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Invoker {
    private final ClientStub stub;
    private final byte[] data;
    private Object return_;
    private final ReentrantLock returnLock = new ReentrantLock();
    private final Condition returnCondition = returnLock.newCondition();

    public void setReturn(Object return_) {
        returnLock.lock();
        this.return_ = return_;
        returnCondition.signal();
        returnLock.unlock();
    }
    Invoker(ClientStub stub, Reference reference, Object...args) throws IOException {
        this.stub = stub;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(args.length * 128);
        var referenceData = reference.getData();
        outputStream.write(referenceData, 0, referenceData.length);
        outputStream.write('(');
        var end = args.length - 1;
        for (var _i = 0; _i <= end; ++_i) {
            var arg = args[_i];
            if (arg instanceof Reference) {
                var argData = ((Reference) arg).getData();
                outputStream.write(argData, 0, argData.length);
            } else {
                new ObjectMapper().writeValue(outputStream, arg);
            }
            if (_i != end) {
                outputStream.write(',');
            }
        }
        outputStream.write(')');
        //BUG Rootive: 一次不必要的拷贝
        data = outputStream.toByteArray();
    }
    public Invoker invoke() {
        stub.getTransmission().toServer(data, this);
        return this;
    }
    public Object waitForReturn() throws InterruptedException {
        returnLock.lock();
        while (return_ == null) {
            returnCondition.await();
        }
        returnLock.unlock();
        return return_;
    }
    public Object ret() throws InterruptedException {
        return waitForReturn();
    }
}
