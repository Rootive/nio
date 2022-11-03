package org.rootive.nio.rudp;

import java.nio.ByteBuffer;

public class RUDPPieces implements Supplier {

    private static final int pieceSize = Constexpr.MTU - Constexpr.headerSize;
    private final ByteBuffer[] pieces;
    private int next;

    public RUDPPieces(ByteBuffer byteBuffer) {
        this(new ByteBuffer[] { byteBuffer });
    }
    public RUDPPieces(ByteBuffer[] byteBuffers) {
        int count = 0;
        for (var byteBuffer : byteBuffers) {
            count += (int) Math.ceil(1.f * byteBuffer.remaining() / pieceSize);
        }
        pieces = new ByteBuffer[count];
        count = 0;
        for (var byteBuffer : byteBuffers) {
            int left, right = byteBuffer.position();
            while (right < byteBuffer.limit()) {
                left = right;
                right = Math.min(right + pieceSize, byteBuffer.limit());
                var size = right - left;
                pieces[count] = ByteBuffer.allocate(size + Constexpr.headerSize);
                pieces[count].position(Constexpr.headerSize).put(byteBuffer.array(), byteBuffer.arrayOffset() + left, size);
                pieces[count].position(0);
                ++count;
            }
        }
    }

    @Override
    public ByteBuffer next(long localCheck, long remoteCheck, long count) {
        return pieces[next++].mark().putLong(localCheck).putLong(remoteCheck).putLong(count).reset();
    }

    @Override
    public boolean empty() {
        return next == pieces.length;
    }
}
