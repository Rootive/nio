package org.rootive.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;

public class LinkedByteBuffer {
    private final Linked<ByteBuffer> linked = new Linked<>();
    private int remaining;
    private int count;

    public Linked<ByteBuffer> getLinked() {
        return linked;
    }
    public int writeTo(ByteChannel byteChannel) throws IOException {
        int ret = remaining;
        while (!linked.isEmpty()) {
            var buffer = removeFirst();
            byteChannel.write(buffer);
            if (buffer.remaining() > 0) {
                addFirst(buffer);
                break;
            }
        }
        return ret - remaining;
    }
    public void writeTo(OutputStream output) throws IOException {
        while (!linked.isEmpty()) {
            var buffer = removeFirst();
            try {
                output.write(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining());
            } catch (IOException e) {
                addFirst(buffer);
                throw e;
            }
        }
    }
    public int readFrom(ByteChannel byteChannel, int elementSize) throws IOException {
        int ret = remaining;
        while (true) {
            ByteBuffer buffer = ByteBuffer.allocate(elementSize);
            int res = byteChannel.read(buffer);
            if (res == -1) {
                ret = -1;
                break;
            } else if (res == 0) {
                ret = remaining - ret;
                break;
            } else {
                buffer.limit(buffer.position());
                buffer.position(0);
                addLast(buffer);
            }
        }
        return ret;
    }
    public int readFrom(InputStream input, int elementSize) throws IOException {
        int ret = remaining;
        while (true) {
            ByteBuffer buffer = ByteBuffer.allocate(elementSize);
            int res = input.read(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining());
            if (res == -1) {
                break;
            } else {
                buffer.limit(buffer.position() + res);
                addLast(buffer);
            }
        }
        return remaining - ret;
    }

    public ByteBuffer toByteBuffer() {
        ByteBuffer ret = ByteBuffer.allocate(remaining);
        var n = linked.head();
        while (n != null) {
            ret.put(n.v.duplicate());
            n = n.right();
        }
        ret.flip();
        return ret;
    }

    public void addFirst(ByteBuffer value) {
        ++count;
        remaining += value.remaining();
        linked.addFirst(value);
    }
    public void addLast(ByteBuffer value) {
        ++count;
        remaining += value.remaining();
        linked.addLast(value);
    }
    public ByteBuffer removeFirst() {
        --count;
        remaining -= linked.head().v.remaining();
        return linked.removeFirst();
    }
    public ByteBuffer removeLast() {
        --count;
        remaining -= linked.tail().v.remaining();
        return linked.removeLast();
    }
    public ByteBuffer head() {
        return linked.isEmpty() ? null : linked.head().v.duplicate();
    }
    public ByteBuffer tail() {
        return linked.isEmpty() ? null : linked.tail().v.duplicate();
    }
    public boolean isEmpty() {
        return linked.isEmpty();
    }
    public void clear() {
        linked.clear();
        remaining = 0;
    }
    public int totalRemaining() {
        return remaining;
    }
    public int count() {
        return count;
    }
}
