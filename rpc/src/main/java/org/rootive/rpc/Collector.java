package org.rootive.rpc;

import org.rootive.util.LinkedByteBuffer;

import java.nio.ByteBuffer;

public class Collector {
    private boolean bDone;
    private final LinkedByteBuffer semi = new LinkedByteBuffer();
    private final LinkedByteBuffer done = new LinkedByteBuffer();
    private Gap gap;

    public LinkedByteBuffer getDone() {
        return done;
    }

    private void tryLineDirectly(ByteBuffer byteBuffer) {
        if (byteBuffer.remaining() >= Constexpr.sizeSize) {
            var duplicate = byteBuffer.duplicate();
            duplicate.mark();
            var size = duplicate.getInt();
            if (duplicate.remaining() >= size) {
                duplicate.limit(duplicate.position() + size);
                byteBuffer.position(duplicate.limit());
                done.addLast(duplicate);
            } else {
                duplicate.reset();
                semi.addLast(duplicate);
                byteBuffer.position(byteBuffer.limit());
            }
        } else {
            semi.addLast(byteBuffer.duplicate());
            byteBuffer.position(byteBuffer.limit());
        }
    }
    private void tryFillHeader(ByteBuffer byteBuffer) {
        if (semi.peekFirst().remaining() < Constexpr.sizeSize) {
            var diff = Constexpr.sizeSize - semi.total();
            if (byteBuffer.remaining() >= diff) {
                var boundary = byteBuffer.position() + diff;
                semi.addLast(byteBuffer.duplicate().limit(boundary));
                semi.defrag();
                byteBuffer.position(boundary);
            } else {
                semi.addLast(byteBuffer.duplicate());
                byteBuffer.position(byteBuffer.limit());
            }
        }
    }
    private void tryLineFromSemi(ByteBuffer byteBuffer) {
        if (semi.total() >= Constexpr.sizeSize) {
            var first = semi.removeFirst();
            first.mark();

            var diff = first.remaining() + semi.total() + byteBuffer.remaining() - first.getInt();
            if (diff >= 0) {
                semi.addFirst(first);
                var boundary = byteBuffer.position() + diff;
                semi.addLast(byteBuffer.duplicate().limit(boundary));
                semi.defrag();
                done.addLast(semi.removeFirst());
                byteBuffer.position(boundary);
            } else {
                first.reset();
                semi.addFirst(first);
                if (byteBuffer.remaining() > 0) {
                    semi.addLast(byteBuffer.duplicate());
                }
                byteBuffer.position(byteBuffer.limit());
            }
        }
    }
    private void tryGap() {
        if (!done.isEmpty()) {
            var last = done.removeLast();
            last.mark();
            if (last.get() == Type.Gap.ordinal()) {
                gap = Gap.parse(last);
                bDone = true;
            } else {
                last.reset();
                done.addLast(last);
            }
        }
    }
    public boolean collect(ByteBuffer byteBuffer) {
        while (!bDone && byteBuffer.remaining() > 0) {
            if (semi.isEmpty()) {
                tryLineDirectly(byteBuffer);
            } else {
                tryFillHeader(byteBuffer);
                tryLineFromSemi(byteBuffer);
            }
            tryGap();
        }
        return bDone;
    }

    public Gap getGap() {
        return gap;
    }
}
