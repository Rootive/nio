package org.rootive.nio;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;

public class ByteBufferList {
    private final LinkedList<ByteBuffer> buffers = new LinkedList<>();
    private int remaining;

    public int writeTo(SocketChannel socketChannel) throws IOException {
        int ret = remaining;
        while (buffers.size() > 0) {
            var buffer = removeFirst();
            socketChannel.write(buffer);
            if (buffer.remaining() > 0) {
                addFirst(buffer);
                break;
            }
        }
        return ret - remaining;
    }
    public void writeTo(OutputStream output) throws IOException {
        while (buffers.size() > 0) {
            var buffer = removeFirst();
            try {
                output.write(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining());
            } catch (IOException e) {
                addFirst(buffer);
                throw e;
            }
        }
    }
    public int readFrom(SocketChannel socketChannel, int elementSize) throws IOException {
        int ret = remaining;
        while (true) {
            ByteBuffer buffer = ByteBuffer.allocate(elementSize);
            int res = socketChannel.read(buffer);
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

    public void addFirst(ByteBuffer value) {
        remaining += value.remaining();
        buffers.addFirst(value);
    }
    public void addLast(ByteBuffer value) {
        remaining += value.remaining();
        buffers.addLast(value);
    }
    public ByteBuffer removeFirst() {
        remaining -= buffers.getFirst().remaining();
        return buffers.removeFirst();
    }
    public int size() {
        return buffers.size();
    }
    public void clear() {
        buffers.clear();
    }
    public int totalRemaining() {
        return remaining;
    }
}
