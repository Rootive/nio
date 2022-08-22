package org.rootive.rpc;

import org.rootive.gadget.ByteBufferList;

import java.nio.ByteBuffer;

public class Collecter {
    public enum State {
        Empty, Semi, Done, Error
    }
    private final ByteBufferList buffers = new ByteBufferList();
    private State state = State.Empty;
    private boolean bString;

    public void clear() {
        buffers.clear();
        state = State.Empty;
        bString = false;
    }
    static public ByteBuffer collect(ByteBuffer read) {
        if (read.remaining() > 0) {
            boolean bString = false;
            for (var _i = read.position(); _i < read.limit(); ++_i) {
                var ch = read.get(_i);
                if (ch == '"') {
                    bString = !bString;
                } else if (ch == '\\') {
                    ++_i;
                } else if (!bString && ch == ';') {
                    ByteBuffer ret = read.duplicate();
                    ret.limit(_i);
                    read.position(_i + 1);
                    return ret;
                }
            }
        }
        return null;
    }
    public State collect(ByteBufferList read) {
        if (state.compareTo(State.Done) >= 0) {
            return state;
        }
        while (read.size() > 0 && state.compareTo(State.Done) < 0) {
            boolean bContinue = true;
            var buffer = read.removeFirst();
            var l = buffer.position();
            var r = buffer.limit();
            while (bContinue && state.compareTo(State.Done) < 0) {
                switch (state) {
                    case Empty -> {
                        if (l < r) {
                            state = State.Semi;
                        } else {
                            bContinue = false;
                        }
                    }
                    case Semi -> {
                        while (l < r) {
                            var ch = buffer.get(l);
                            if (bString) {
                                if (ch == '\\') {
                                    ++l;
                                } else if (ch == '"') {
                                    bString = false;
                                }
                            } else {
                                if (ch == '"') {
                                    bString = true;
                                } else if (ch == ';') {
                                    var duplicate = buffer.duplicate();
                                    buffer.limit(l);
                                    buffers.addLast(buffer);
                                    state = State.Done;
                                    duplicate.position(l + 1);
                                    if (duplicate.remaining() > 0) {
                                        read.addFirst(duplicate);
                                    }
                                    break;
                                }
                            }
                            ++l;
                        }
                        if (l == r) {
                            buffers.addLast(buffer);
                            bContinue = false;
                        }
                    }
                }
            }


        }
        return state;
    }
    public int collectedSize() {
        return buffers.totalRemaining();
    }
    @Override
    public String toString() {
        return new String(buffers.toByteArray());
    }
    public byte[] toByteArray() {
        return buffers.toByteArray();
    }
}
