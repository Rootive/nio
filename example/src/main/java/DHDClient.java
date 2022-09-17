//import org.rootive.nio.EventLoopThread;
//import org.rootive.p2p.RUDPPeer;
//import org.rootive.p2p.raft.Constexpr;
//import org.rootive.p2p.raft.RUDPRaft;
//import org.rootive.rpc.*;
//
//import java.io.IOException;
//import java.net.InetSocketAddress;
//import java.util.Scanner;
//
//public class DHDClient {
//    static private final Signature raftSig = new Signature(RUDPRaft.class, "raft");
//    static private final Signature dhdSig = new Signature(DistributedHTTPDownloader.class, "dhd");
//    static private Function downloadFunc;
//    static private Signature downloadSig;
//
//    static private Function findLeaderFunc;
//    static private Signature findLeaderSig;
//
//    static {
//        try {
//            downloadFunc = new Function(DistributedHTTPDownloader.class.getMethod("download", String.class));
//            downloadSig = new Signature(downloadFunc, "download");
//
//            findLeaderFunc = new Function(RUDPRaft.class.getMethod("findLeader"));
//            findLeaderSig = new Signature(findLeaderFunc, "findLeader");
//        } catch (NoSuchMethodException e) {
//            e.printStackTrace();
//        }
//
//    }
//
//    private final RUDPPeer peer;
//
//    public DHDClient(RUDPPeer peer) {
//        this.peer = peer;
//    }
//
//    private int leaderId;
//
//    private void updateLeaderId() throws IOException, InterruptedException, TransmissionException, InvocationException, ParseException {
//        var functor = findLeaderFunc.newFunctor(findLeaderSig, raftSig);
//        while (true) {
//            var ret = peer.callLiteral(Constexpr.addresses[leaderId], functor);
//            if (ret.wait(2000)) {
//                int res = (int) ret.get();
//                if (res == leaderId) {
//                    System.out.println("leader confirmed");
//                    break;
//                } else if (res >= 0) {
//                    leaderId = res;
//                    System.out.println("leader change to " + leaderId);
//                    break;
//                } else {
//                    System.out.println("election and wait");
//                    Thread.sleep(2000);
//                    System.out.println("\tcontinue");
//                }
//            } else {
//                leaderId = (leaderId + 1) % Constexpr.addresses.length;
//                System.out.println("timeout and try " + leaderId);
//            }
//        }
//    }
//    public void download(String link) throws IOException, TransmissionException, InvocationException, ParseException, InterruptedException {
//        updateLeaderId();
//        var functor = downloadFunc.newFunctor(downloadSig, dhdSig, link);
//        DistributedHTTPDownloader.Download res = (DistributedHTTPDownloader.Download) peer.callBytes(Constexpr.addresses[leaderId], functor).get();
//        if (res.record().isEmpty()) {
//            if (res.bWaiting() > 0) {
//                System.out.println("queueing " + res.bWaiting());
//            } else {
//                if (res.size() > 0) {
//                    System.out.println("downloading 0/" + res.size());
//                } else {
//                    System.out.println("meta downloading");
//                }
//            }
//        } else {
//            var record = res.record().split(",");
//            var totalSize = Long.parseLong(record[0]);
//            long currentSize = 0;
//            for (int _i = 1; _i < record.length; ++_i) {
//                if (record[_i].isEmpty()) {
//                    continue;
//                }
//                var piece = record[_i].split(" ");
//                if (piece.length < 2) {
//                    continue;
//                }
//                currentSize += Long.parseLong(piece[1]) - Long.parseLong(piece[0]);
//            }
//            System.out.println("downloading " + currentSize + "/" + totalSize);
//        }
//    }
//
//    public static void boot() throws IOException, InterruptedException, TransmissionException, InvocationException, ParseException {
//        EventLoopThread peerThread = new EventLoopThread();
//        peerThread.start();
//
//        RUDPPeer peer = new RUDPPeer(peerThread.getEventLoop(), 1);
//        DHDClient client = new DHDClient(peer);
//        peer.init(new InetSocketAddress(46555));
//
//        Scanner in = new Scanner(System.in);
//        while (true) {
//            System.out.print("input link: ");
//            var link = in.nextLine();
//            while (true) {
//                client.download(link);
//                Thread.sleep(1000);
//            }
//        }
//    }
//}
