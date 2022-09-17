import org.rootive.dhd.DHTTPDownloader;
import org.rootive.nio.EventLoopThread;
import org.rootive.p2p.RUDPPeer;
import org.rootive.p2p.raft.Constexpr;
import org.rootive.rpc.*;

import java.io.IOException;
import java.net.InetSocketAddress;

public class DHDClient0 {
    public static void main(String[] args) throws TransmissionException, InvocationException, IOException, ParseException, InterruptedException, NoSuchMethodException {
        //DHDClient.boot();
        var addRequestFunc = new Function(DHTTPDownloader.class.getMethod("addRequest", String.class, String.class));
        var addRequestSig = new Signature(addRequestFunc, "addRequest");
        var dhdSig = new Signature(DHTTPDownloader.class, "dhd");
        var addressSig = new Signature(Signature.namespaceStringOf(String.class), "address");
        var functor = addRequestFunc.newFunctor(addRequestSig, dhdSig, "https://upos-sz-mirror08ct.bilivideo.com/upgcxcode/98/67/330436798/330436798-1-192.mp4?e=ig8euxZM2rNcNbRVhwdVhwdlhWdVhwdVhoNvNC8BqJIzNbfq9rVEuxTEnE8L5F6VnEsSTx0vkX8fqJeYTj_lta53NCM=&uipk=5&nbs=1&deadline=1663063988&gen=playurlv2&os=08ctbv&oi=837395164&trid=594724001eb04dacadf148536a4227f4T&mid=0&platform=html5&upsig=d20ef892d97fb3221e6b5911b929afc4&uparams=e,uipk,nbs,deadline,gen,os,oi,trid,mid,platform&bvc=vod&nettype=0&bw=70723&orderid=0,1&logo=80000000", addressSig);

        EventLoopThread peerThread = new EventLoopThread();
        peerThread.start();

        RUDPPeer peer = new RUDPPeer(peerThread.getEventLoop(), 1);
        peer.init(new InetSocketAddress(46555));
        var leaderId = 0;

        peer.callLiteral(Constexpr.addresses[leaderId], functor).get();
    }
}
