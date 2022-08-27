package org.rootive.gadget;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Loop {
    @FunctionalInterface
    public interface Runner {
        void run() throws Exception;
    }

    private final AtomicBoolean bStarted = new AtomicBoolean();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private Linked<Runner> runners = new Linked<>();
    private final long threadId = Thread.currentThread().getId();

    private void handleException(Exception e) {
        e.printStackTrace();
    }

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
            while (runners.isEmpty()) {
                try { condition.await(); }
                catch (InterruptedException e) { handleException(e); }
            }
            var tmp = runners;
            runners = new Linked<>();
            lock.unlock();

            var n = tmp.head();
            while (n != null) {
                try {
                    n.v.run();
                } catch (Exception e) {
                    handleException(e);
                }
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
    public void run(Runner runner) throws Exception {
        if (isThread()) { runner.run(); }
        else { queue(runner); }
    }
    public void queue(Runner runner) {
        lock.lock();
        runners.addLast(runner);
        condition.signal();
        lock.unlock();
    }
}
