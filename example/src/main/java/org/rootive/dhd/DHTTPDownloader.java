package org.rootive.dhd;

import org.rootive.nio.EventLoop;
import org.rootive.nio.Loop;
import org.rootive.nio.LoopThread;
import org.rootive.p2p.RUDPPeer;
import org.rootive.p2p.raft.Constexpr;
import org.rootive.p2p.raft.RUDPRaft;
import org.rootive.p2p.raft.Raft;
import org.rootive.rpc.Collector;
import org.rootive.rpc.Function;
import org.rootive.rpc.Gap;
import org.rootive.rpc.Signature;
import org.rootive.util.Linked;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class DHTTPDownloader {
    static private Signature dhdSig;
    static private Function addRequestFunc;
    static private Function addEntryFunc;
    static private Signature addEntrySig;
    static private Function assignFunc;
    static private Signature assignSig;
    static private Function addDoneFunc;
    static private Signature addDoneSig;

    static {
        try {
            dhdSig = new Signature(DHTTPDownloader.class, "dhd");
            addRequestFunc = new Function(DHTTPDownloader.class.getMethod("addRequest", String.class, String.class));
            addEntryFunc = new Function(DHTTPDownloader.class.getMethod("addEntry", Request.class, long.class, String.class));
            addEntrySig = new Signature(addEntryFunc, "addEntry");
            assignFunc = new Function(DHTTPDownloader.class.getMethod("assign", Assignment.class));
            assignSig = new Signature(assignFunc, "assign");
            addDoneFunc = new Function(DHTTPDownloader.class.getMethod("addDone", String.class, Piece.class));
            addDoneSig = new Signature(addDoneFunc, "addDone");
        } catch (Exception e) {
            assert false;
        }
    }

    static final private int assignmentsCountLine = 1;
    static final private long pieceSize = 8 * 1024 * 1024;
    static final private int handlePeriod = 1000;

    private final RUDPPeer peer;
    private final RUDPRaft raft;
    private final Loop loop;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private final String dataDirectory;

    static public record Request(String link, int server, String client) { }
    private final Linked<Request> requests = new Linked<>();

    static public record MetaFuture(Request request, CompletableFuture<Void> future) { }
    private final Linked<MetaFuture> metaFutures = new Linked<>();

    private final HashSet<Request> unhandledRequests = new HashSet<>();

    static public final class Entry {
        public Request request;
        public long offset;
        public long size;
        public String md5;

        public Entry() { }
        public Entry(Request request, long offset, long size, String md5) {
            this.request = request;
            this.offset = offset;
            this.size = size;
            this.md5 = md5;
        }

        public Request getRequest() {
            return request;
        }
        public void setRequest(Request request) {
            this.request = request;
        }
        public long getOffset() {
            return offset;
        }
        public void setOffset(long offset) {
            this.offset = offset;
        }
        public long getSize() {
            return size;
        }
        public void setSize(long size) {
            this.size = size;
        }
        public String getMd5() {
            return md5;
        }
        public void setMd5(String md5) {
            this.md5 = md5;
        }
    }
    private final Linked<Entry> entries = new Linked<>();

    static public record Assignment(Entry entry, long begin, long end, int server) { }
    private final HashMap<String, Linked<Assignment>> assignments = new HashMap<>();

    static public record UnhandledAssignment(Request request, long begin, long end, long size, String md5) { }
    private final HashSet<UnhandledAssignment> unhandledAssignments = new HashSet<>();

    static public record Piece(long begin, long end, long size, int server, String path) { }
    private final HashMap<String, ArrayList<Piece>> done = new HashMap<>();

    private final Timer timer = new Timer();

    private int next = 0;
    private int getNext() {
        var ret = next;
        next = (next + 1) % Constexpr.addresses.length;
        return next++;
    }

    static private final ByteBuffer gap = Gap.get(Gap.Context.CallLiteral, 0);
    static private byte[] addBytes(ByteBuffer a, ByteBuffer b) {
        byte[] ret = new byte[a.remaining() + b.remaining()];
        System.arraycopy(a.array(), a.arrayOffset() + a.position(), ret, 0, a.remaining());
        System.arraycopy(b.array(), b.arrayOffset() + b.position(), ret, a.remaining(), b.remaining());
        return ret;
    }

    public DHTTPDownloader(RUDPPeer peer, RUDPRaft raft, Loop loop, String dataDirectory) {
        this.peer = peer;
        this.raft = raft;
        this.loop = loop;
        this.dataDirectory = dataDirectory;
        new File(dataDirectory).mkdirs();

        peer.register("dhd", this);
        peer.register("addRequest", addRequestFunc);
        peer.register("addEntry", addEntryFunc);
        peer.register("assign", assignFunc);
        peer.register("addDone", addDoneFunc);

        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                handleRequest();
                handleEntry();
                handleAssignments();
            }
        }, handlePeriod, handlePeriod);
    }

    public void addRequest(String link, String client) {
        var request = new Request(link, raft.getId(), client);
        requests.addLast(request);
    }
    public void handleRequest() {
        loop.run(() -> {
            if (!requests.isEmpty()) {
                addMetaFutures(requests.removeFirst());
            }
        });
    }
    private void addMetaFutures(Request request) {
        var httpRequest = HttpRequest
                .newBuilder(URI.create(request.link))
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .build();
        metaFutures.addLast(null);
        var n = metaFutures.tail();
        var future = httpClient
                .sendAsync(httpRequest, HttpResponse.BodyHandlers.discarding())
                .thenAccept((res) -> loop.run(() -> {
                    metaFutures.split(n);
                    var base64 = res.headers().firstValue("content-md5").get();
                    var md5 = new String(Base64.getEncoder().encode(base64.getBytes()));
                    sendAddEntry(
                            request
                            , res.headers().firstValueAsLong("Content-Length").getAsLong()
                            , md5
                    );
                    unhandledRequests.add(request);
                }));
        n.v = new MetaFuture(request, future);
    }

    public void addEntry(Request request, long size, String md5) {
        var n = entries.head();
        while (n != null) {
            if (Objects.equals(n.v.md5, md5)) {
                break;
            }
            n = n.right();
        }
        var inAssignments = assignments.get(md5);
        var inDone = done.get(md5);
        if (n == null && inAssignments == null && inDone == null) {
            entries.addLast(new Entry(request, 0, size, md5));
        }

        if (raft.getId() == request.server) {
            if (unhandledRequests.contains(request)) {
                // reply
                unhandledRequests.remove(request);
            }
        }
    }
    private void sendAddEntry(Request request, long size, String md5) {
        try {
            var functor = addEntryFunc.newFunctor(addEntrySig, dhdSig, request, size, md5);
            raft.getLoop().run(() -> {
                try {
                    raft.send(addBytes(functor.getData(), gap));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private void handleEntry() {
        raft.getLoop().run(() -> {
            if (raft.getState() == Raft.State.Leader) {
                loop.run(() -> {
                    if (!entries.isEmpty()) {
                        var n = entries.head();
                        var begin = n.v.offset;
                        var end = Math.min(n.v.offset + pieceSize, n.v.size);
                        sendAssign(new Assignment(n.v, begin, end, getNext()));
                    }
                });
            }
        });
    }

    public void assign(Assignment a) {
        var n = entries.head();
        n.v.offset = a.end;
        if (n.v.offset == n.v.size) {
            entries.removeFirst();
        }

        var res = assignments.get(a.entry.md5);
        if (res == null) {
            res = new Linked<>();
            assignments.put(a.entry.md5, res);
        }
        res.addLast(a);

        if (a.server == raft.getId()) {
            unhandledAssignments.add(new UnhandledAssignment(a.entry.request, a.begin, a.end, a.entry.size, a.entry.md5));
        }
    }
    private void sendAssign(Assignment a) {
        try {
            var functor = assignFunc.newFunctor(assignSig, dhdSig, a);
            raft.getLoop().run(() -> {
                try {
                    raft.send(addBytes(functor.getData(), gap));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private void handleAssignments() {
        loop.run(() -> {
            for (var a : unhandledAssignments) {
                var httpRequest = HttpRequest
                        .newBuilder(URI.create(a.request.link))
                        .header("Range", "bytes=" + a.begin + "-" + a.end)
                        .GET()
                        .build();
                var filename = "" + System.currentTimeMillis();
                var file = new File(dataDirectory + "/" + filename);
                var future = httpClient
                        .sendAsync(httpRequest, HttpResponse.BodyHandlers.ofFile(file.toPath()))
                        .exceptionally((e) -> {
                            e.printStackTrace();
                            return null;
                        })
                        .thenAccept((response) -> {
                            System.out.println("piece down");
                            loop.run(() -> {
                                sendAddDone(a.md5, new Piece(a.begin, a.end, a.size, raft.getId(), filename));
                            });
                        });
            }
            unhandledAssignments.clear();
        });
    }

    public void addDone(String md5, Piece piece) {
        var dl = done.computeIfAbsent(md5, k -> new ArrayList<>());
        dl.add(piece);

        var al = assignments.get(md5);
        if (al != null) {
            var n = al.head();
            while (n != null) {
                if (n.v.begin == piece.begin && n.v.end == piece.end) {
                    al.split(n);
                    break;
                }
                n = n.right();
            }
        }

        if (raft.getId() == piece.server) {
            unhandledAssignments.removeIf((a) -> a.begin == piece.begin && a.end == piece.end && a.md5.equals(md5));
        }
    }
    private void sendAddDone(String md5, Piece piece) {
        try {
            var functor = addDoneFunc.newFunctor(addDoneSig, dhdSig, md5, piece);
            raft.getLoop().run(() -> {
                try {
                    raft.send(addBytes(functor.getData(), gap));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void apply(byte[] b) {
        var c = new Collector();
        if (c.collect(ByteBuffer.wrap(b))) {
            peer.getServerStub().handleReceived(c);
        }
    }



    static public void boot(int id) throws InterruptedException, IOException {
        var raftThread = new LoopThread();
        raftThread.start();
        var dhdThread = new LoopThread();
        dhdThread.start();
        HashMap<String, Loop> map = new HashMap<>();
        map.put(Signature.namespaceStringOf(RUDPRaft.class), raftThread.getLoop());
        map.put(Signature.namespaceStringOf(DHTTPDownloader.class), dhdThread.getLoop());

        EventLoop peerLoop = new EventLoop();
        peerLoop.init();

        var peer = new RUDPPeer(peerLoop, 1);
        peer.setDispatcher((n, r) -> map.get(n).run(r));
        var raft = new RUDPRaft(peer, raftThread.getLoop(), id, "D:\\.tmp\\raft\\" + id);
        var dhd = new DHTTPDownloader(peer, raft, dhdThread.getLoop(), "D:\\.tmp\\dhd\\" + id);
        raft.init(dhd::apply);
        peer.init(Constexpr.addresses[id]);

        peerLoop.start();
    }
    public byte[] getPiece(String filename) throws IOException {
        var in = new FileInputStream(dataDirectory + "/" + filename);
        var ret = in.readAllBytes();
        in.close();
        return ret;
    }
}
