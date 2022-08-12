package org.rootive.nio;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class EventLoopThread {
    @FunctionalInterface
    interface ThreadInitFunction {
        void accept(EventLoop eventLoop) throws Exception;
    }

    private final Thread thread = new Thread(this::threadFunction);
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private EventLoop eventLoop;
    private ThreadInitFunction threadInitFunction;
    private Exception exception;

    private void threadFunction() {
        try {
            var el = new EventLoop();
            el.init();
            if (threadInitFunction != null) {
                threadInitFunction.accept(el);
            }
            lock.lock();
            eventLoop = el;
            condition.signal();
            lock.unlock();
            eventLoop.start();
        } catch (Exception e) {
            lock.lock();
            exception = e;
            condition.signal();
            lock.unlock();
        }
    }

    public EventLoop getEventLoop() {
        return eventLoop;
    }
    public long getThreadId() { return thread.getId(); }
    public boolean isStarted() { return eventLoop.isStarted(); }
    public void setThreadInitFunction(ThreadInitFunction threadInitFunction) {
        this.threadInitFunction = threadInitFunction;
    }

    public void start() throws Exception {
        thread.start();
        lock.lock();
        while (eventLoop == null && exception == null) {
            condition.await();
        }
        lock.unlock();
        if (exception != null) { throw exception; }
    }
    public void stop() throws InterruptedException {
        eventLoop.stop();
        thread.join();
    }
}
