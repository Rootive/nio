package org.rootive.rpc;

//BUG Rootive: 需要对Reference统一管理吗？
public class ClientStub {
    private final Transmission transmission;

    public ClientStub(Transmission transmission) {
        this.transmission = transmission;
    }
    Transmission getTransmission() { return transmission; }
    public Reference signatureIs(Signature s) {
        return new Reference(this, s);
    }
    public Reference sig(Signature s) {
        return signatureIs(s);
    }

}
