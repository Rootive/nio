package org.rootive.rpc;

public interface Transmission {
    void send(byte[] data, Invoker invoker) throws Exception;
}
