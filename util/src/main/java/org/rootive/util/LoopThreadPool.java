package org.rootive.util;

import java.util.ArrayList;

public class LoopThreadPool {
    private int last = -1;
    private final ArrayList<LoopThread> threads = new ArrayList<>();

    public LoopThreadPool(int count) {
        for (int _i = 0; _i < count; ++_i) {
            threads.add(new LoopThread());
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
    public LoopThread get() {
        last = (last + 1) % threads.size();
        return threads.get(last);
    }
    public LoopThread get(int hash) {
        return threads.get(hash % threads.size());
    }
}
