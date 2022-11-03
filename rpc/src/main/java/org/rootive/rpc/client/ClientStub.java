package org.rootive.rpc.client;

import org.rootive.rpc.Collector;
import org.rootive.rpc.Gap;
import org.rootive.rpc.Line;
import org.rootive.rpc.Signature;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public abstract class ClientStub {
    private record StoreEntry(Signature method, Line[] lines, Gap gap, Return<?> ret) { }
    private final HashMap<Long, Return<?>> map = new HashMap<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicLong next = new AtomicLong();

    protected abstract void send(Object with, ByteBuffer[] byteBuffer);
    private void put(ArrayList<StoreEntry> storeEntries) {
        lock.lock();
        for (var storeEntry : storeEntries) {
            map.put(storeEntry.gap.token, storeEntry.ret);
        }
        lock.unlock();
    }

    static public class Session {
        private final ArrayList<StoreEntry> storeEntries = new ArrayList<>();
        public <T> Return<T> literal(Class<T> retClass, Signature method, Line...lines) {
            return store(new Gap((byte) (Gap.CALL | Gap.LITERAL | Gap.CONTINUE)), retClass, method, lines);
        }
        public Return<byte[]> bytes(Signature method, Line...lines) {
            return store(new Gap((byte) (Gap.CALL | Gap.BYTES | Gap.CONTINUE)), byte[].class, method, lines);
        }
        public Return<Boolean> keep(String identifier, Signature method, Line...lines) {
            return store(new Gap((byte) (Gap.CALL | Gap.KEEP | Gap.CONTINUE), identifier), Boolean.class, method, lines);
        }
        private <T> Return<T> store(Gap gap, Class<T> retClass, Signature method, Line[] lines) {
            var ret = new Return<>(retClass);
            storeEntries.add(new StoreEntry(method, lines, gap, ret));
            return ret;
        }
        public void end(ClientStub clientStub, Object with, byte struct) {
            storeEntries.get(storeEntries.size() - 1).gap.setStruct(struct);

            var count = 0;
            for (var storeEntry : storeEntries) {
                count += storeEntry.lines.length + 2;
                storeEntry.gap.token = clientStub.next.getAndIncrement();
            }
            clientStub.put(storeEntries);
            var byteBuffers = new ByteBuffer[count];
            var index = 0;
            for (var storeEntry : storeEntries) {
                byteBuffers[index++] = storeEntry.method.toByteBuffer();
                for (var line : storeEntry.lines) {
                    byteBuffers[index++] = line.toByteBuffer();
                }
                byteBuffers[index++] = storeEntry.gap.line();
            }
            clientStub.send(with, byteBuffers);
        }
    }

    public void handle(List<Collector> collectors) {
        var rets = new Return<?>[collectors.size()];
        lock.lock();
        for (var index = 0; index < collectors.size(); ++index) {
            rets[index] = map.remove(collectors.get(index).getGap().token);
        }
        lock.unlock();
        for (var index = 0; index < collectors.size(); ++index) {
            if (rets[index] != null) {
                rets[index].set(collectors.get(index));
            }
        }
    }
    public void dropAll(String message) {
        lock.lock();
        map.forEach((check, ret) -> ret.set(new Gap((byte) (Gap.RETURN | Gap.PARSE_EXCEPTION | Gap.SINGLE)), message.getBytes()));
        map.clear();
        lock.unlock();
    }
}
