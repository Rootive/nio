package org.rootive.nio;

import java.util.ArrayList;

public class EventLoopThreadPool {
    private int last = -1;
    private ArrayList<EventLoopThread> threads = new ArrayList<>();

    public EventLoopThreadPool(int count) {
        for (int _i = 0; _i < count; ++_i) {
            threads.add(new EventLoopThread());
        }
    }

    public ArrayList<EventLoopThread> getThreads() {
        return threads;
    }
    public void setThreadInitFunction(EventLoopThread.ThreadInitFunction threadInitFunction) {
        for (var th : threads) {
            th.setThreadInitFunction(threadInitFunction);
        }
    }

    public void start() throws Exception {
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
