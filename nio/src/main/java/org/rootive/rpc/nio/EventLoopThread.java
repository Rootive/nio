package org.rootive.rpc.nio;

import java.io.IOException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class EventLoopThread {
    private final Thread thread = new Thread(this::threadFunction);
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private EventLoop eventLoop;
    private Consumer<EventLoop> threadInitFunction;

    private void threadFunction() {
        var e = new EventLoop();
        try {
            e.init();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        if (threadInitFunction != null) {
            threadInitFunction.accept(e);
        }
        lock.lock();
        eventLoop = e;
        condition.signal();
        lock.unlock();
        eventLoop.start();
    }

    public EventLoop getEventLoop() {
        return eventLoop;
    }
    public long getThreadId() { return thread.getId(); }
    public boolean isStarted() { return eventLoop.isStarted(); }
    public void setThreadInitFunction(Consumer<EventLoop> threadInitFunction) {
        this.threadInitFunction = threadInitFunction;
    }

    public void start() throws InterruptedException {
        thread.start();
        lock.lock();
        while (eventLoop == null) {
            condition.await();
        }
        lock.unlock();
    }
    public void stop() throws InterruptedException {
        eventLoop.stop();
        thread.join();
    }
    public void join() throws InterruptedException {
        thread.join();
    }
}
