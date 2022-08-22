package org.rootive.rpc;

public interface MessengerTransmission {
    void send(byte[] data, Messenger messenger) throws Exception;
}
