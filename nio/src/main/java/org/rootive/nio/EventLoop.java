package org.rootive.nio;

public interface EventLoop {

    default void init() throws Exception { }
    void start();
    void stop();
    void run(Runnable runnable);
    void queue(Runnable runnable);

    long getThreadID();
    boolean isStarted();

}
