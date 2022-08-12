package org.rootive.rpc;

import java.nio.ByteBuffer;

public class Collecter {
    public enum State {
        Empty, Semi, Done, Error
    }
    private ByteBufferList buffers = new ByteBufferList();
    private State state = State.Empty;
    int rbrac = 0;
    private void reset() {
        buffers.clear();
        state = State.Empty;
        rbrac = 0;
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
                        while (l < r) {
                            if (buffer.get(l) == '@') {
                                buffer.position(l);
                                state = State.Semi;
                                break;
                            }
                            ++l;
                        }
                        if (l == r) {
                            bContinue = false;
                        }
                    }
                    case Semi -> {
                        while (l < r) {
                            if (buffer.get(l) == ')') {
                                ++rbrac;
                            }
                            if (rbrac == 2) {
                                var limit = l + 1;
                                if (limit == buffer.limit()) {
                                    buffers.addLast(buffer);
                                } else {
                                    ByteBuffer bufferDuplicate = buffer.duplicate();
                                    buffer.limit(limit);
                                    buffers.addLast(buffer);
                                    bufferDuplicate.position(limit);
                                    read.addFirst(bufferDuplicate);
                                }
                                state = State.Done;
                                break;
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
}
