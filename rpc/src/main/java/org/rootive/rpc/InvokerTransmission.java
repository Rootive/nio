package org.rootive.rpc;

public interface InvokerTransmission {
    void send(byte[] data, Invoker invoker) throws Exception;
}
