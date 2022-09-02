package org.rootive.nio;

import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;

public class EventLoop {
    private static final int timeout = 10 * 1000;
    private final long threadId = Thread.currentThread().getId();
    private final AtomicBoolean bStarted = new AtomicBoolean();
    private final AtomicBoolean bRunnables = new AtomicBoolean();
    private final ReentrantLock lock = new ReentrantLock();
    private LinkedList<Runnable> runnables = new LinkedList<>();
    private Selector selector;

    public long getThreadId() {
        return threadId;
    }
    public boolean isStarted() { return bStarted.get(); }
    public boolean isThread() {
        return Thread.currentThread().getId() == threadId;
    }
    private void wakeup() {
        selector.wakeup();
    }
    void handleException(Exception e) {
        e.printStackTrace();
    }

    public SelectionKey add(SelectableChannel c, int ops, Handler h) throws ClosedChannelException {
        var ret = c.register(selector, ops);
        ret.attach(h);
        return ret;
    }
    public void init() throws IOException {
        selector = Selector.open();
    }
    public void start() {
        assert threadId == Thread.currentThread().getId();
        assert !bStarted.get();
        bStarted.set(true);
        while (bStarted.get()) {
            try {
                if (selector.select(timeout) > 0) {
                    var selectedKeys = selector.selectedKeys();
                    for (var k : selectedKeys) {
                        ((Handler) k.attachment()).handleEvent();
                    }
                    selectedKeys.clear();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            bRunnables.set(true);

            lock.lock();
            var another = runnables;
            runnables = new LinkedList<>();
            lock.unlock();

            for (var runner : another) {
                runner.run();
            }
            bRunnables.set(false);
        }
    }
    public void stop() {
        bStarted.set(false);
        wakeup();
    }
    public void run(Runnable runnable) {
        if (isThread()) {
            runnable.run();
        } else {
            queue(runnable);
        }
    }
    public void queue(Runnable runnable) {
        lock.lock();
        runnables.add(runnable);
        lock.unlock();
        if (!isThread() || bRunnables.get()) {
            wakeup();
        }
    }
}
