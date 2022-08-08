package org.rootive.nio;

import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

/**
 * IO多路复用的TCP服务器类，主线程负责监听，子线程负责业务。
 * <br>
 * 启动它需要3步：
 * <ol>
 *     <li>设置回调方法,详见{@link TcpConnection}下的static方法</li>
 *     <li>调用{@link TcpServer#init(InetSocketAddress)}</li>
 *     <li>调用{@link TcpServer#start()}</li>
 * </ol>
 * {@link TcpServer#threadsCount}决定子线程的数量。
 * <br>
 * 在测试代码中，TcpServerTest展示了一个echo服务器的实现，其将日志打印到标准输出。可以使用同目录下的SimpleClient配合测试。
 * <br>
 * （测试代码在根目录下的test文件夹中）
 * @author Rootive
 */
public class TcpServer {
    private final int threadsCount = 4;
    private EventLoopThreadPool threads = new EventLoopThreadPool(threadsCount);
    private EventLoop eventLoop = new EventLoop();
    private Acceptor acceptor = new Acceptor();

    /**
     * @param threadInitFunction 子线程启动后首先执行的方法。该方法的执行属于服务器启动时，其产生的异常将从{@link TcpServer#init(InetSocketAddress)}抛出。
     */
    public void setThreadInitFunction(EventLoopThread.ThreadInitFunction threadInitFunction) {
        threads.setThreadInitFunction(threadInitFunction);
    }
    /**
     * 初始化，最大的作用是集中服务器启动时的所有异常。
     * @param local 监听地址。
     * @throws Exception 任何异常都意味着服务器启动失败。这些异常的来源有2种：java.nio、用户设置的threadInitFunction。
     * @see TcpServer#setThreadInitFunction(EventLoopThread.ThreadInitFunction) 
     */
    public void init(InetSocketAddress local) throws Exception {
        eventLoop.init();
        acceptor.bind(local, this::newConnection);
        acceptor.register(eventLoop);
        threads.start();
    }

    /**
     * 服务器完全运行。运行时的异常全部由EventLoop::handleException处理，暂时只是将异常打印。
     * <br>
     * 服务器运行时产生的异常不会使任何线程退出。
     * <br>
     * 对于用户设置的回调方法产生的异常：放弃执行该方法；对于java.nio产生的异常：重试。
     * <br>
     * 暂时没有提供正常停止的方法。
     */
    public void start() {
        eventLoop.start();
    }

    private void newConnection(SocketChannel sc) {
        var e = threads.get().getEventLoop();
        e.queue(() -> new TcpConnection(sc).register(e));
    }
}
