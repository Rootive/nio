package org.rootive.rpc;

public class Return {
    public enum Status {
        Done,
        TransmissionException,
        ParseException,
        InvocationException
    }

    public Return() { }

    public Return(Status stat, Object data) {
        this.stat = (byte) stat.ordinal();
        this.data = data;
    }

    public byte stat;
    public Object data;
}
