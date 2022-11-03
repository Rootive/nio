package org.rootive.nio;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class EventLoopThread {
    private final Thread thread = new Thread(this::target);
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final Class<? extends EventLoop> eventLoopClass;
    private EventLoop eventLoop;
    private Consumer<EventLoop> threadInitFunction;

    private void target() {
        EventLoop e;
        try {
            e = eventLoopClass.getConstructor().newInstance();
            e.init();
        } catch (Exception exception) {
            exception.printStackTrace();
            return;
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

    public EventLoopThread(Class<? extends EventLoop> eventLoopClass) {
        this.eventLoopClass = eventLoopClass;
    }

    public EventLoop getEventLoop() {
        return eventLoop;
    }
    public long getThreadID() { return thread.getId(); }
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
