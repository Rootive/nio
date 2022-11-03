package org.rootive.rpc.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;

public class LinkedByteBuffer {
    private final Linked<ByteBuffer> linked = new Linked<>();
    private int total;
    private int count;

    public int writeTo(ByteChannel byteChannel) throws IOException {
        int ret = total;
        while (!linked.isEmpty()) {
            var byteBuffer = removeFirst();
            byteChannel.write(byteBuffer);
            if (byteBuffer.remaining() > 0) {
                addFirst(byteBuffer);
                break;
            }
        }
        return ret - total;
    }
    public int readFrom(ByteChannel byteChannel, int unitSize) throws IOException {
        int ret = total;
        while (true) {
            ByteBuffer byteBuffer = ByteBuffer.allocate(unitSize);
            int res = byteChannel.read(byteBuffer);
            if (res == -1) {
                ret = -1;
                break;
            } else if (res == 0) {
                ret = total - ret;
                break;
            } else {
                byteBuffer.flip();
                addLast(byteBuffer);
            }
        }
        return ret;
    }

    public void defrag() {
        ByteBuffer ret = ByteBuffer.allocate(total);
        while (!linked.isEmpty()) {
            ret.put(linked.removeFirst());
        }
        addLast(ret.flip());
    }

    public void addFirst(ByteBuffer value) {
        ++count;
        total += value.remaining();
        linked.addFirst(value);
    }
    public void addLast(ByteBuffer value) {
        ++count;
        total += value.remaining();
        linked.addLast(value);
    }

    public ByteBuffer removeFirst() {
        --count;
        total -= linked.head().v.remaining();
        return linked.removeFirst();
    }
    public ByteBuffer removeLast() {
        --count;
        total -= linked.tail().v.remaining();
        return linked.removeLast();
    }

    public ByteBuffer peekFirst() {
        return linked.isEmpty() ? null : linked.head().v.duplicate();
    }
    public ByteBuffer peekLast() {
        return linked.isEmpty() ? null : linked.tail().v.duplicate();
    }

    public boolean isEmpty() {
        return linked.isEmpty();
    }
    public void clear() {
        linked.clear();
        total = 0;
        count = 0;
    }

    public int total() {
        return total;
    }
    public int count() {
        return count;
    }
}
