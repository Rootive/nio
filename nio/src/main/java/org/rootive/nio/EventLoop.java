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
    @FunctionalInterface
    interface Runner {
        void run() throws Exception;
    }

    private static final int timeout = 10 * 1000;
    private final long threadId = Thread.currentThread().getId();
    private final AtomicBoolean bStarted = new AtomicBoolean();
    private final AtomicBoolean bRunners = new AtomicBoolean();
    private final ReentrantLock runnersLock = new ReentrantLock();
    private LinkedList<Runner> runners = new LinkedList<>();
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
                        try { ((org.rootive.nio.Handler) k.attachment()).handleEvent(); }
                        catch (Exception e) { handleException(e); }
                    }
                    selectedKeys.clear();
                }
            } catch (IOException e) { handleException(e); }

            bRunners.set(true);

            runnersLock.lock();
            var another = runners;
            runners = new LinkedList<>();
            runnersLock.unlock();

            for (var runner : another) {
                try { runner.run(); }
                catch (Exception e) { handleException(e); }
            }
            bRunners.set(false);
        }
    }
    public void stop() {
        bStarted.set(false);
        wakeup();
    }
    public void run(Runner runner) throws Exception {
        if (isThread()) {
            runner.run();
        } else {
            queue(runner);
        }
    }
    public void queue(Runner runner) {
        runnersLock.lock();
        runners.add(runner);
        runnersLock.unlock();
        if (!isThread() || bRunners.get()) {
            wakeup();
        }
    }
}
