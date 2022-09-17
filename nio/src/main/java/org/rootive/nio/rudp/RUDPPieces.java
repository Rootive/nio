package org.rootive.nio.rudp;

import java.nio.ByteBuffer;

public class RUDPPieces implements Supplier {

    private static final int pieceSize = Constexpr.MTU - Constexpr.headerSize;
    private final ByteBuffer[] ds;
    private int next;

    public RUDPPieces(ByteBuffer data) {
        ds = new ByteBuffer[(int) Math.ceil(1.f * data.remaining() / pieceSize)];
        int left, right = data.position();
        int count = 0;
        while (right < data.limit()) {
            left = right;
            right = Math.min(right + pieceSize, data.limit());
            var size = right - left;
            ds[count] = ByteBuffer.allocate(size + Constexpr.headerSize);
            ds[count].position(Constexpr.headerSize).put(data.array(), data.arrayOffset() + left, size);
            ds[count].position(0);
            ++count;
        }

    }

    @Override
    public ByteBuffer next(long localCheck, long remoteCheck, long count) {
        return ds[next++].mark().putLong(localCheck).putLong(remoteCheck).putLong(count).reset();
    }

    @Override
    public boolean empty() {
        return next == ds.length;
    }
}
