package org.rootive.rpc;

public interface Transmission {
    void toServer(byte[] data, Invoker client);
}
