package org.rootive.nio.rudp;

import org.rootive.nio.EventLoop;
import org.rootive.util.Linked;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class RUDPConnection {
    public record Datagram(int count, ByteBuffer data) { }

    long lastReceive;

    private final RUDPManager server;
    private final SocketAddress remote;
    private final EventLoop eventLoop;
    private final boolean addressCompare;

    private BiConsumer<RUDPConnection, Linked<ByteBuffer>> readCallback;
    private Consumer<RUDPConnection> resetCallback;

    private long localCheck;
    private long remoteCheck = 0;

    private final Linked<Supplier> unsent = new Linked<>();
    private final Linked<Datagram> unconfirmed = new Linked<>();
    private Linked<ByteBuffer> received = new Linked<>();
    private int sentCount;
    private int receivedCount;

    public Object context;

    private static boolean addressCompare(InetSocketAddress a, InetSocketAddress b) {
        var aAddress = Arrays.stream(a.getAddress().getHostAddress().split("\\.")).mapToInt(Integer::parseInt).toArray();
        var bAddress = Arrays.stream(b.getAddress().getHostAddress().split("\\.")).mapToInt(Integer::parseInt).toArray();
        for (var _i = 0; _i < 4; ++_i) {
            if (aAddress[_i] == bAddress[_i]) {
                continue;
            }
            return aAddress[_i] < bAddress[_i];
        }
        assert a.getPort() != b.getPort();
        return a.getPort() < b.getPort();
    }
    RUDPConnection(RUDPManager server, SocketAddress local, SocketAddress remote, EventLoop eventLoop) {
        this.server = server;
        this.remote = remote;
        this.eventLoop = eventLoop;
        localCheck = System.currentTimeMillis();
        addressCompare = addressCompare((InetSocketAddress) local, (InetSocketAddress) remote);
    }

    public SocketAddress getRemote() {
        return remote;
    }
    public EventLoop getEventLoop() {
        return eventLoop;
    }
    void setReadCallback(BiConsumer<RUDPConnection, Linked<ByteBuffer>> readCallback) {
        this.readCallback = readCallback;
    }
    void setResetCallback(Consumer<RUDPConnection> resetCallback) {
        this.resetCallback = resetCallback;
    }

    public void message(Supplier supplier) {
        eventLoop.run(() -> {
            unsent.addLast(supplier);
        });
    }
    void next() {
        eventLoop.run(() -> {
            if (unconfirmed.isEmpty()) {
                if (!unsent.isEmpty()) {
                    var supplier = unsent.removeFirst();
                    while (!supplier.empty()) {
                        var byteBuffer = supplier.next(localCheck, remoteCheck, ++sentCount);
                        unconfirmed.addLast(new Datagram(sentCount, byteBuffer));
                        server.send(this, byteBuffer.duplicate());
                    }
                }
            } else {
                var n = unconfirmed.head();
                while (n != null) {
                    server.send(this, n.v.data.duplicate());
                    n = n.right();
                }
            }
        });
    }

    void reset() {
        if (resetCallback != null) {
            resetCallback.accept(this);
        }
        unsent.clear();
        unconfirmed.clear();
        received.clear();
        sentCount = 0;
        receivedCount = 0;
    }
    private void heartbeat() {
        server.send(this, ByteBuffer.allocate(Constexpr.checkSize).putLong(localCheck).putLong(remoteCheck).flip());
    }
    private boolean updateCheck(long _remoteCheck, long _localCheck) {
        //System.out.println("localCheck: " + localCheck + "\t_localCheck: " + _localCheck + "\tremoteCheck: " + remoteCheck + "\t_remoteCheck: " + _remoteCheck);
        boolean ret = false;
        if (remoteCheck > 0 && _remoteCheck < remoteCheck && _localCheck > localCheck) {
            if (addressCompare) {
                localCheck = _localCheck;
                remoteCheck = _remoteCheck;
            } else {
                heartbeat();
            }
            ret = true;
        } else if (_remoteCheck < remoteCheck) {
            heartbeat();
        } else if (_localCheck > localCheck) {
            localCheck = _localCheck;
            remoteCheck = _remoteCheck;
            reset();
            ret = true;
        } else if (remoteCheck > 0) {
            if (_remoteCheck == remoteCheck) {
                if (_localCheck == localCheck) {
                    ret = true;
                } else if (_localCheck == 0) {
                    ret = true;
                } else {
                    reset();
                    remoteCheck = _remoteCheck + 1;
                    heartbeat();
                }
            } else {
                if (_localCheck == localCheck) {
                    reset();
                    remoteCheck = _remoteCheck + 1;
                    heartbeat();
                } else if (_localCheck == 0) {
                    reset();
                    remoteCheck = _remoteCheck;
                    ret = true;
                } else {
                    reset();
                    remoteCheck = _remoteCheck + 1;
                    heartbeat();
                }
            }
        } else if (remoteCheck == 0) {
            if (_localCheck == 0) {
                remoteCheck = _remoteCheck;
                ret = true;
            } else if (_localCheck == localCheck) {
                remoteCheck = _remoteCheck;
                ret = true;
            } else {
                reset();
                remoteCheck = _remoteCheck + 1;
                heartbeat();
            }
        }
        return ret;
    }
    void handleReceive(ByteBuffer b) {
        eventLoop.run(() -> {
            if (b.remaining() >= Constexpr.checkSize) {
                if (updateCheck(b.getLong(), b.getLong())) {
                    if (b.remaining() >= Constexpr.countSize) {
                        var count = b.getLong();
                        if (b.remaining() > 0) {
                            handleMessage(count, b);
                        } else {
                            handleMessageConfirm(count);
                        }
                    }
                }
            }
        });
    }
    private void confirm(long count) {
        server.send(this, ByteBuffer.allocate(Constexpr.headerSize).putLong(localCheck).putLong(remoteCheck).putLong(count).flip());
    }
    private void handleMessage(long c, ByteBuffer b) {
        confirm(c);

        var n = received.head();
        var _i = receivedCount + 1;
        while (_i < c) {
            if (n == null) {
                received.addLast(null);
                n = received.tail();
            }

            ++_i;
            n = n.right();
        }
        if (n == null) {
            received.addLast(b);
        } else {
            n.v = b;
        }

        n = received.head();
        while (n != null) {
            if (n.v == null) {
                break;
            }

            ++receivedCount;
            n = n.right();
        }
        Linked<ByteBuffer> ready;
        if (n == null) {
            ready = received;
            received = new Linked<>();
        } else {
            ready = received.breakLeft(n);
        }

        if (!ready.isEmpty() && readCallback != null) {
            readCallback.accept(this, ready);
        }
    }
    private void handleMessageConfirm(long c) {
        var n = unconfirmed.head();
        while (n != null) {
            if (c == n.v.count) {
                unconfirmed.escape(n);
                break;
            } else if (c < n.v.count) {
                break;
            }
            n = n.right();
        }
    }
}
