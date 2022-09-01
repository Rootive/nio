package org.rootive.rpc;

public class Return {
    public enum Status {
        Done,
        TransmissionException,
        ParseException,
        InvocationException
    }
    public byte stat;
    public Object data;
}
