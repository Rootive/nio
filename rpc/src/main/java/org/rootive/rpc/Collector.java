package org.rootive.rpc;

import java.nio.ByteBuffer;

public class Collector extends Gap {
    private boolean bDone;
    private final LinkedByteBuffer semi = new LinkedByteBuffer();
    private final LinkedByteBuffer done = new LinkedByteBuffer();

    public LinkedByteBuffer getDone() {
        return done;
    }

    public boolean collect(ByteBuffer b) {
        while (!bDone && b.remaining() > 0) {
            if (semi.isEmpty()) {
                if (b.remaining() >= Constexpr.headerSize) {
                    var bd = b.duplicate();
                    bd.mark();
                    var size = bd.getInt() + Constexpr.typeSize;
                    if (bd.remaining() >= size) {
                        bd.limit(bd.position() + size);
                        b.position(bd.limit());
                        done.addLast(bd);
                    } else {
                        bd.reset();
                        semi.addLast(bd);
                        b.position(b.limit());
                    }
                } else {
                    semi.addLast(b.duplicate());
                    b.position(b.limit());
                }
            } else {
                if (semi.totalRemaining() >= Constexpr.headerSize) {
                    var h = semi.removeFirst();
                    h.mark();
                    var size = h.getInt() + Constexpr.typeSize;
                    var diff = size - (h.remaining() + semi.totalRemaining());
                    if (b.remaining() >= diff) {
                        semi.addFirst(h);
                        var boundary = b.position() + diff;
                        semi.addLast(b.duplicate().limit(boundary));
                        b.position(boundary);
                        var res = semi.toByteBuffer();
                        semi.clear();
                        done.addLast(res);
                    } else {
                        h.reset();
                        semi.addFirst(h);
                        semi.addLast(b.duplicate());
                        b.position(b.limit());
                    }
                } else if (semi.totalRemaining() + b.remaining() >= Constexpr.headerSize) {
                    var boundary = b.position() + Constexpr.headerSize - semi.totalRemaining();
                    semi.addLast(b.duplicate().limit(boundary));
                    var res = semi.toByteBuffer();
                    semi.clear();
                    semi.addLast(res);
                    b.position(boundary);
                } else {
                    semi.addLast(b.duplicate());
                    b.position(b.limit());
                }
            }

            if (!done.isEmpty()) {
                var tail = done.removeLast();
                tail.mark();
                if (tail.get() == Type.Gap.ordinal()) {
                    tail.reset();
                    parse(tail);
                    if (!done.isEmpty()) {
                        bDone = true;
                    }
                } else {
                    tail.reset();
                    done.addLast(tail);
                }
            }
        }

        return bDone;
    }

}
