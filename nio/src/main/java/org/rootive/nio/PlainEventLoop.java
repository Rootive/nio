package org.rootive.nio;

import org.rootive.util.Linked;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class PlainEventLoop implements EventLoop{
    private final long threadID = Thread.currentThread().getId();
    private final AtomicBoolean bStarted = new AtomicBoolean();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private Linked<Runnable> runnables = new Linked<>();


    @Override
    public void start() {
        assert threadID == Thread.currentThread().getId();
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

    @Override
    public void stop() {
        bStarted.set(false);
        lock.lock();
        condition.signal();
        lock.unlock();
    }

    @Override
    public void run(Runnable runnable) {
        if (getThreadID() == Thread.currentThread().getId()) { runnable.run(); }
        else { queue(runnable); }
    }

    @Override
    public void queue(Runnable runnable) {
        lock.lock();
        runnables.addLast(runnable);
        condition.signal();
        lock.unlock();
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
