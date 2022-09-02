//import org.rootive.nio.EventLoopThread;
//
//import java.net.InetSocketAddress;
//import java.util.ArrayList;
//import java.util.Arrays;
//
//public class RUDPRaft {
//    static public class Context {
//        private int nextIndex;
//        private int matchIndex;
//    }
//    static public class Entry {
//        public int term;
//        public String log;
//
//        public int getTerm() {
//            return term;
//        }
//
//        public void setTerm(int term) {
//            this.term = term;
//        }
//
//        public String getLog() {
//            return log;
//        }
//
//        public void setLog(String log) {
//            this.log = log;
//        }
//    }
//    static public class AppendEntriesReturn {
//        public int term;
//        public boolean success;
//
//        public int getTerm() {
//            return term;
//        }
//
//        public void setTerm(int term) {
//            this.term = term;
//        }
//
//        public boolean isSuccess() {
//            return success;
//        }
//
//        public void setSuccess(boolean success) {
//            this.success = success;
//        }
//    }
//
//    private final EventLoopThread pt = new EventLoopThread();
//    private RUDPPeer p;
//
//    private final Raft.State state = Raft.State.Follower;
//
//    private int currentTerm;
//    private String votedFor;
//    private ArrayList<Entry> logs;
//
//    private int commitIndex;
//    private int lastApplied;
//
//    public RUDPRaft(int timersCount, int threadsCount, InetSocketAddress local) {
//        pt.setThreadInitFunction((e) -> {
//            p = new RUDPPeer(e, timersCount, threadsCount);
//            p.init(local);
//            p.setContextSetter((c) -> new Context());
//        });
//    }
//    public void init() throws Exception {
//        pt.start();
//    }
//
//    private void save() {
//        System.out.println("currentTerm: " + currentTerm + " votedFor: " + votedFor + " logs: " + logs);
//    }
//    private Entry getEntry(int index) {
//        return logs.get(index);
//    }
//    private boolean checkIndex(int index) {
//        return index < logs.size();
//    }
//    private void removeFrom(int index) {
//        logs.subList(index, logs.size()).clear();
//    }
//    private void append(Entry[] es) {
//        logs.addAll(Arrays.asList(es));
//    }
//
////    public AppendEntriesReturn appendEntries(int term, String leaderID, int prevLogIndex, int prevLogTerm, Entry[] entries, int leaderCommit) {
////        AppendEntriesReturn ret = new AppendEntriesReturn();
////        ret.term = currentTerm;
////        if (term < currentTerm) {
////            ret.success = false;
////            return ret;
////        }
////        currentTerm = term;
////        if (!checkIndex(prevLogIndex) || getEntry(prevLogIndex).term != prevLogTerm) {
////            ret.success = false;
////            return ret;
////        }
////        int currentIndex = prevLogIndex + 1;
////        if (checkIndex(currentIndex) && getEntry(currentIndex).term != entries[0].term) {
////            removeFrom(currentIndex);
////        }
////        append(entries);
////        if (leaderCommit > commitIndex) {
////            commitIndex = Math.min(leaderCommit, )
////        }
////    }
//}
