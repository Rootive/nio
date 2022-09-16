package org.rootive.p2p.raft;

import org.rootive.nio.Loop;
import org.rootive.p2p.RUDPPeer;
import org.rootive.rpc.*;
import org.rootive.util.Linked;
import org.rootive.util.Storage;
import org.rootive.util.StorageList;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class RUDPRaft {
    static public record Entry(int term, byte[] log) { }
    static private final int appendPeriod = 2000;
    static private final int missDelay = 4000;
    static private final int electionCheckPeriodMin = 200;
    static private final int electionCheckPeriodMax = 600;
    static private final int electionCheckLine = 8;

    static private final Signature raftSig = new Signature(RUDPRaft.class, "raft");

    static private Function appendEntriesFunc;
    static private Signature appendEntriesSig;

    static private Function requestVoteFunc;
    static private Signature requestVoteSig;

    static private Function findLeaderFunc;

    static private Function sendFunc;
    static private Signature sendSig;


    static {
        try {
            appendEntriesFunc = new Function(RUDPRaft.class.getMethod("appendEntries", int.class, int.class, int.class, int.class, Entry[].class, int.class));
            appendEntriesSig = new Signature(appendEntriesFunc, "appendEntries");

            requestVoteFunc = new Function(RUDPRaft.class.getMethod("requestVote", int.class, int.class, int.class, int.class));
            requestVoteSig = new Signature(requestVoteFunc, "requestVote");

            findLeaderFunc = new Function(RUDPRaft.class.getMethod("findLeader"));

            sendFunc = new Function(RUDPRaft.class.getMethod("send", byte[].class));
            sendSig = new Signature(sendFunc, "send");
        } catch (NoSuchMethodException e) {
            assert false;
        }
    }

    private final Storage<Integer> currentTerm;
    private final Storage<Integer> votedFor;
    private final StorageList<Entry> log;

    private int commitIndex;
    private int lastApplied;

    private final int[] nextIndex = new int[Constexpr.addresses.length];
    private final int[] matchIndex = new int[Constexpr.addresses.length];
    private final Linked<_AppendEntries> unconfirmed = new Linked<>();

    private Raft.State state;
    private final int id;
    private int leaderId = -2;
    private final Random rand = new Random();

    private final ScheduledThreadPoolExecutor timers = new ScheduledThreadPoolExecutor(1);
    private ScheduledFuture<?> future;
    private Consumer<byte[]> apply;
    private int electionCheckCount;

    private final RUDPPeer peer;
    private final Loop loop;

    public RUDPRaft(RUDPPeer peer, Loop loop, int id, String directory) {
        this.peer = peer;
        this.loop = loop;
        this.id = id;

        new File(directory).mkdirs();
        currentTerm = new Storage<>(directory + "/currentTerm");
        votedFor = new Storage<>(directory + "/votedFor");
        log = new StorageList<>(directory + "/log");
    }

    public Loop getLoop() {
        return loop;
    }
    public int getId() {
        return id;
    }
    public Raft.State getState() {
        return state;
    }

    public void init(Consumer<byte[]> apply) throws IOException {
        this.apply = apply;

        peer.register("raft", this);
        peer.register("appendEntries", appendEntriesFunc);
        peer.register("requestVote", requestVoteFunc);
        peer.register("findLeader", findLeaderFunc);
        peer.register("send", sendFunc);

        currentTerm.init(Integer.class, () -> 0);
        votedFor.init(Integer.class, () -> null);
        log.init(Entry.class, () -> {
            var _log = new ArrayList<Entry>();
            _log.add(new Entry(0, "".getBytes()));
            return _log;
        });
        System.out.println("saved log count: " + log.get().size());

        state = Raft.State.Follower;
        setMiss();
    }
    public int findLeader() {
        if (state != Raft.State.Candidate) {
            return leaderId;
        }
        return -2;
    }
    public void send(byte[] data) throws IOException {
        switch (state) {
            case Leader -> {
                future.cancel(false);
                log.get().add(new Entry(currentTerm.get(), data));
                try {
                    log.set();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                setAppend();
            }
            case Candidate -> {
                var _id = (id + 1) % Constexpr.addresses.length;
                if (_id == leaderId) {
                    _id = (id - 1) % Constexpr.addresses.length;
                }
                var functor = sendFunc.newFunctor(sendSig, raftSig, data);
                peer.callLiteral(Constexpr.addresses[_id], functor);
            }
            case Follower -> {
                var functor = sendFunc.newFunctor(sendSig, raftSig, data);
                peer.callLiteral(Constexpr.addresses[leaderId], functor);
            }
        }
    }

    private void toFollower() {
        System.out.println("to follower");
        future.cancel(false);
        state = Raft.State.Follower;
        setMiss();
    }
    private void toCandidate() {
        System.out.println("to candidate");
        future.cancel(false);
        state = Raft.State.Candidate;
        startElection();
    }
    private void toLeader() {
        System.out.println("to leader");
        future.cancel(false);
        leaderId = id;
        state = Raft.State.Leader;
        Arrays.fill(nextIndex, log.get().size());
        Arrays.fill(matchIndex, 0);
        unconfirmed.clear();
        setAppend();
    }

    private void newTerm(int newTerm) throws IOException {
        currentTerm.set(newTerm);
        if (votedFor.get() != null) {
            votedFor.set(null);
        }
    }
    private void setMiss() {
        future = timers.schedule(() -> loop.run(this::toCandidate), missDelay, TimeUnit.MILLISECONDS);
    }
    private void startElection() {
        //System.out.println("start election");
        electionCheckCount = 0;
        try {
            newTerm(currentTerm.get() + 1);
            votedFor.set(id);
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            var lastLogIndex = log.get().size() - 1;
            var lastLogTerm = log.get().get(lastLogIndex).term;
            var rets = new Return[Constexpr.addresses.length];
            for (var _i = 0; _i < Constexpr.addresses.length; ++_i) {
                if (id == _i) { continue; }
                var functor = requestVoteFunc.newFunctor(requestVoteSig, raftSig, currentTerm.get(), id, lastLogIndex, lastLogTerm);
                rets[_i] = peer.callLiteral(Constexpr.addresses[_i], functor);
            }
            setElectionCheck(rets, currentTerm.get());
        } catch (IOException e) {
            e.printStackTrace();
            toFollower();
        }
    }

    private int getElectionCheckPeriod() {
        return rand.nextInt(electionCheckPeriodMin, electionCheckPeriodMax);
    }
    private void setElectionCheck(Return[] rets, int term) {
        var period = getElectionCheckPeriod();
        future = timers.scheduleAtFixedRate(() -> loop.run(() -> {
            if (state != Raft.State.Candidate || term < currentTerm.get()) {
                return;
            }
            //System.out.println("election check " + electionCheckCount);
            int tCount = 1;
            for (var _i = 0; _i < Constexpr.addresses.length; ++_i) {
                if (id == _i) { continue; }
                if (rets[_i].isSet()) {
                    //System.out.println("\t" + _i + " set");
                    try {
                        var res = (RequestVote) rets[_i].get();
                        if (res.term > currentTerm.get()) {
                            newTerm(res.term);
                            toFollower();
                        } else {
                            if (res.voteGranted) {
                                ++tCount;
                            }
                        }
                    } catch (TransmissionException | InvocationException | ParseException | IOException | InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            var line = Constexpr.addresses.length / 2;
            if (tCount > line) {
                toLeader();
            } else {
                if (++electionCheckCount >= electionCheckLine) {
                    toCandidate();
                }
            }
        }), period, period, TimeUnit.MILLISECONDS);
    }

    static private record _AppendEntries(int id, Return data, int lastLogIndex, int nextLogIndex) { }
    private void setAppend() {
        future = timers.scheduleAtFixedRate(() -> loop.run(() -> {
            if (state != Raft.State.Leader) {
                return;
            }
            System.out.println("broadcast append");
            var n = unconfirmed.head();
            while (n != null) {
                if (n.v.data.isSet()) {
                    try {
                        var res = (AppendEntries) n.v.data.get();
                        if (res.term > currentTerm.get()) {
                            newTerm(res.term);
                            toFollower();
                            return;
                        }
                        if (res.success) {
                            matchIndex[n.v.id] = n.v.nextLogIndex - 1;
                        } else {
                            nextIndex[n.v.id] = n.v.lastLogIndex;
                        }
                    } catch (TransmissionException | InvocationException | ParseException | InterruptedException | IOException e) {
                        System.out.println(e.getMessage());
                    }

                    var clone = n;
                    n = n.right();
                    unconfirmed.split(clone);
                } else {
                    n = n.right();
                }
            }
            matchIndex[id] = log.get().size() - 1;

            var clone = Arrays.copyOf(matchIndex, matchIndex.length);
            Arrays.sort(clone);
            commitIndex = Math.max(clone[(clone.length - 1) / 2], commitIndex);

            for (var _i = 0; _i < Constexpr.addresses.length; ++_i) {
                if (id == _i) { continue; }
                var prevLogIndex = nextIndex[_i] - 1;
                var prevLogTerm = log.get().get(prevLogIndex).term;
                Object[] entries;
                int _nextIndex;
                if (matchIndex[_i] == prevLogIndex) { _nextIndex = log.get().size(); }
                else { _nextIndex = Math.min(log.get().size(), nextIndex[_i] + 1); }
                entries = log.get().subList(nextIndex[_i], _nextIndex).toArray();
                nextIndex[_i] = _nextIndex;
                try {
                    var functor = appendEntriesFunc.newFunctor(appendEntriesSig, raftSig, currentTerm.get(), id, prevLogIndex, prevLogTerm, entries, commitIndex);
                    unconfirmed.addLast(new _AppendEntries(_i, peer.callLiteral(Constexpr.addresses[_i], functor), prevLogIndex, _nextIndex));
                } catch (IOException e) { e.printStackTrace(); }
            }

            while (commitIndex > lastApplied) {
                apply.accept(log.get().get(++lastApplied).log);
            }
        }), 0, appendPeriod, TimeUnit.MILLISECONDS);
    }

    static private record AppendEntries(int term, boolean success) { }
    public AppendEntries appendEntries(int term, int leaderId, int prevLogIndex, int prevLogTerm, Entry[] entries, int leaderCommit) throws IOException {
        if (term < currentTerm.get()) {
            return new AppendEntries(currentTerm.get(), false);
        }

        System.out.println("appended by " + leaderId);
        future.cancel(false);
        this.leaderId = leaderId;
        if (term > currentTerm.get()) {
            newTerm(term);
        }
        if (state != Raft.State.Follower) {
            toFollower();
        }

        if (prevLogIndex >= log.get().size() || log.get().get(prevLogIndex).term != prevLogTerm) {
            setMiss();
            return new AppendEntries(currentTerm.get(), false);
        }

        if (log.get().size() > prevLogIndex + 1) {
            log.get().subList(prevLogIndex + 1, log.get().size()).clear();
        }
        log.get().addAll(Arrays.asList(entries));
        log.set();

        if (leaderCommit > commitIndex) {
            commitIndex = Math.min(leaderCommit, log.get().size() - 1);
        }

        while (commitIndex > lastApplied) {
            apply.accept(log.get().get(++lastApplied).log);
        }

        setMiss();
        return new AppendEntries(currentTerm.get(), true);
    }

    static private record RequestVote(int term, boolean voteGranted) { }
    public RequestVote requestVote(int term, int candidateId, int lastLogIndex, int lastLogTerm) throws IOException {
        //System.out.print("requestVote parameters: term: " + term + " candidateId: " + candidateId + " voteGranted: ");
        var _votedFor = votedFor.get();

        if (term > currentTerm.get()) {
            currentTerm.set(term);
            _votedFor = null;
        } else if (term < currentTerm.get()) {
            return new RequestVote(currentTerm.get(), false);
        }

        var localLastLogIndex = log.get().size() - 1;
        var localLastLogTerm = log.get().get(localLastLogIndex).term;
        var voteGranted = (_votedFor == null || Objects.equals(_votedFor, candidateId))
                && (lastLogTerm > localLastLogTerm || (lastLogTerm == localLastLogTerm && lastLogIndex >= localLastLogIndex));
        if (voteGranted) {
            _votedFor = candidateId;
            toFollower();
        }

        if (!Objects.equals(_votedFor, votedFor.get())) {
            votedFor.set(_votedFor);
        }

        return new RequestVote(currentTerm.get(), voteGranted);
    }
}
