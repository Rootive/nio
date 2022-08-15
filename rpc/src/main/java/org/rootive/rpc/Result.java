package org.rootive.rpc;

public class Result {
    public static class Status {
        static final public int DONE = 200;

        static final public int SERIALIZATION_ERROR = 600;
        static final public int DESERIALIZATION_ERROR = 601;

        static final public int INVOCATION_ERROR = 700;
        static final public int BAD_REGISTER = 701;
        static final public int BAD_PARAMETERS = 702;
        static final public int BAD_REFERENCE = 703;

    }
    private int stat;
    private String msg;
    private byte[] data;

    public int getStat() {
        return stat;
    }

    public String getMsg() {
        return msg;
    }

    public byte[] getData() {
        return data;
    }

    public void setStat(int stat) {
        this.stat = stat;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public void setData(byte[] data) {
        this.data = data;
    }
}
