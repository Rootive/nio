package org.rootive.nio;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

public class SelectEventLoop implements EventLoop {

    private static final int timeout = 10 * 1000;
    private final long threadID = Thread.currentThread().getId();
    private final AtomicBoolean bStarted = new AtomicBoolean();
    private final AtomicBoolean bRunnables = new AtomicBoolean();
    private final ReentrantLock lock = new ReentrantLock();
    private LinkedList<Runnable> runnables = new LinkedList<>();
    private Selector selector;

    public SelectionKey add(SelectableChannel channel, int ops, Handler handler) throws ClosedChannelException {
        var ret = channel.register(selector, ops);
        ret.attach(handler);
        return ret;
    }

    @Override
    public void init() throws IOException {
        selector = Selector.open();
    }

    @Override
    public void start() {
        assert threadID == Thread.currentThread().getId();
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

    @Override
    public void stop() {
        bStarted.set(false);
        selector.wakeup();
    }

    @Override
    public void run(Runnable runnable) {
        if (getThreadID() == Thread.currentThread().getId()) {
            runnable.run();
        } else {
            queue(runnable);
        }
    }

    @Override
    public void queue(Runnable runnable) {
        lock.lock();
        runnables.add(runnable);
        lock.unlock();
        if (getThreadID() != Thread.currentThread().getId() || bRunnables.get()) {
            selector.wakeup();
        }
    }

    @Override
    public long getThreadID() {
        return threadID;
    }

    @Override
    public boolean isStarted() {
        return bStarted.get();
    }
}
