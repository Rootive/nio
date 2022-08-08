package org.rootive.nio;

import java.nio.channels.SelectableChannel;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;

public class EventLoop {
    @FunctionalInterface
    interface Handler {
        void handle(SelectionKey sk) throws Exception;
    }
    @FunctionalInterface
    interface Runner {
        void run() throws Exception;
    }

    private static final int timeout = 10 * 1000;
    private static final int initialCapacity = 32;
    private final long threadId = Thread.currentThread().getId();
    private final AtomicBoolean bStarted = new AtomicBoolean();
    private final AtomicBoolean bRunners = new AtomicBoolean();
    private final ReentrantLock runnersLock = new ReentrantLock();
    private final HashMap<SelectionKey, Handler> map = new HashMap<>(initialCapacity);
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
    private void handleException(Exception e) {
        e.printStackTrace();
    }

    public void init() throws IOException {
        selector = Selector.open();
    }
    public SelectionKey add(SelectableChannel sc, Handler h, int ops) throws IOException {
        var ret = sc.register(selector, ops);
        map.put(ret, h);
        return ret;
    }
    public void remove(SelectionKey sk) {
        System.out.println(Thread.currentThread().getId() + " map " + map.size());
        map.remove(sk);
        System.out.println(Thread.currentThread().getId() + " map " + map.size());
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
                        try { map.get(k).handle(k); }
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
    public void queue(Runner runner) {
        runnersLock.lock();
        runners.add(runner);
        runnersLock.unlock();
        if (!isThread() || bRunners.get()) {
            wakeup();
        }
    }
}
