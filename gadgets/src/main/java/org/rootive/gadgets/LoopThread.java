package org.rootive.gadgets;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class LoopThread {
    private final Thread thread = new Thread(this::threadFunction);
    private Loop loop;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();

    private void threadFunction() {
        lock.lock();
        loop = new Loop();
        condition.signal();
        lock.unlock();
        loop.start();
    }

    public Loop getLoop() {
        return loop;
    }
    public long getThreadId() { return thread.getId(); }
    public boolean isStarted() { return loop.isStarted(); }

    public void start() throws Exception {
        thread.start();
        lock.lock();
        while (loop == null) {
            condition.await();
        }
        lock.unlock();
    }
    public void stop() throws InterruptedException {
        loop.stop();
        thread.join();
    }
    public void join() throws InterruptedException {
        thread.join();
    }
}
