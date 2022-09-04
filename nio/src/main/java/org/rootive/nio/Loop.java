package org.rootive.nio;

import org.rootive.util.Linked;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Loop {
    private final AtomicBoolean bStarted = new AtomicBoolean();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private Linked<Runnable> runnables = new Linked<>();
    private final long threadId = Thread.currentThread().getId();

    public long getThreadId() {
        return threadId;
    }
    public boolean isStarted() { return bStarted.get(); }
    public boolean isThread() {
        return Thread.currentThread().getId() == threadId;
    }

    public void start() {
        assert threadId == Thread.currentThread().getId();
        assert !bStarted.get();
        bStarted.set(true);
        while (bStarted.get()) {

            lock.lock();
            while (runnables.isEmpty()) {
                try { condition.await(); }
                catch (InterruptedException e) { e.printStackTrace(); }
            }
            var another = runnables;
            runnables = new Linked<>();
            lock.unlock();

            var n = another.head();
            while (n != null) {
                n.v.run();
                n = n.right();
            }

        }
    }
    public void stop() {
        bStarted.set(false);
        lock.lock();
        condition.signal();
        lock.unlock();
    }
    public void run(Runnable runnable) {
        if (isThread()) { runnable.run(); }
        else { queue(runnable); }
    }
    public void queue(Runnable runnable) {
        lock.lock();
        runnables.addLast(runnable);
        condition.signal();
        lock.unlock();
    }
}
