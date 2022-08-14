package org.rootive.log;

import org.rootive.gadget.ByteBufferList;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Logger {
    public enum Level {
        All, Trace, Debug, Info, Warn, Error, Fatal, Off
    }
    static final private ReentrantLock initLock = new ReentrantLock();
    static final private Condition initCondition = initLock.newCondition();
    static final private ReentrantLock buffersLock = new ReentrantLock();
    static final private Condition buffersCondition = buffersLock.newCondition();
    static final private Thread thread = new Thread(Logger::run);
    static final private AtomicBoolean bStarted = new AtomicBoolean();
    static private ByteBufferList available;
    static private ByteBufferList fulled;
    static private OutputStream output;
    static private Level level = Level.Off;

    private Logger() {  }
    static public Level getLevel() {
        return level;
    }
    static public void setLevel(Level level) {
        Logger.level = level;
    }
    static private void handleException(Exception e) {
        e.printStackTrace();
    }
    static private void run() {
        initLock.lock();
        available = new ByteBufferList();
        fulled = new ByteBufferList();
        initCondition.signal();
        initLock.unlock();
        do {
            buffersLock.lock();
            try {
                while (available.size() == 0) {
                    buffersCondition.await();
                }
            } catch (InterruptedException e) {
                handleException(e);
            }
            var temporary = available;
            available = fulled;
            buffersLock.unlock();
            fulled = temporary;
            while (fulled.size() > 0) {
                try {
                    fulled.writeTo(output);
                } catch (IOException e) {
                    handleException(e);
                }
            }
            try {
                output.flush();
            } catch (IOException e) {
                handleException(e);
            }
        } while (bStarted.get());
        buffersLock.lock();
        while (available.size() > 0) {
            try {
                available.writeTo(output);
            } catch (IOException e) {
                handleException(e);
            }
        }
        buffersLock.unlock();
    }

    static public void start(Level level, OutputStream output) throws InterruptedException {
        setLevel(level);
        Logger.output = output;
        bStarted.set(true);
        thread.start();
        initLock.lock();
        while  (available == null || fulled == null) {
            initCondition.await();
        }
        initLock.unlock();
    }
    static public void stop() throws InterruptedException {
        bStarted.set(false);
        thread.join();
    }
    static public void add(ByteBuffer buffer) {
        buffersLock.lock();
        available.addLast(buffer);
        buffersCondition.signal();
        buffersLock.unlock();
    }
}
