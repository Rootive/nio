package org.rootive.nio;

import java.util.ArrayList;
import java.util.function.Consumer;

public class EventLoopThreadPool {
    private int last = -1;
    private final ArrayList<EventLoopThread> threads = new ArrayList<>();

    public EventLoopThreadPool(int count) {
        for (int _i = 0; _i < count; ++_i) {
            threads.add(new EventLoopThread());
        }
    }

    public ArrayList<EventLoopThread> getThreads() {
        return threads;
    }
    public void setThreadInitFunction(Consumer<EventLoop> threadInitFunction) {
        for (var th : threads) {
            th.setThreadInitFunction(threadInitFunction);
        }
    }
    public int count() {
        return threads.size();
    }

    public void start() throws InterruptedException {
        for (var th : threads) {
            th.start();
        }
    }
    public void stop() throws InterruptedException {
        for (var th : threads) {
            th.stop();
        }
    }
    public EventLoopThread get() {
        last = (last + 1) % threads.size();
        return threads.get(last);
    }
    public EventLoopThread get(int hash) {
        return threads.get(hash % threads.size());
    }
}
