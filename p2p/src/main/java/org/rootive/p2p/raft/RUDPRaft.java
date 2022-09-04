package org.rootive.p2p.raft;

import org.rootive.nio.EventLoop;
import org.rootive.p2p.RUDPPeer;
import org.rootive.rpc.*;
import org.rootive.util.Linked;
import org.rootive.util.Storage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class RUDPRaft {
    static public record Entry(int term, String log) { }
    static private final int appendPeriod = 4000;
    static private final int appendMissDelay = 8000;
    static private final int electionCheckPeriod = 200;
    static private final int electionCheckLine = 4;

    static private final Signature raftSig = new Signature(RUDPRaft.class, "raft");

    static private Function appendEntriesFunc;
    static private Signature appendEntriesSig;

    static private Function requestVoteFunc;
    static private Signature requestVoteSig;

    static {
        try {
            appendEntriesFunc = new Function(RUDPRaft.class.getMethod("appendEntries", int.class, int.class, int.class, int.class, Entry[].class, int.class));
            appendEntriesSig = new Signature(appendEntriesFunc, "appendEntries");

            requestVoteFunc = new Function(RUDPRaft.class.getMethod("requestVote", int.class, int.class, int.class, int.class));
            requestVoteSig = new Signature(requestVoteFunc, "requestVote");
        } catch (NoSuchMethodException e) {
            assert false;
        }
    }

    private final Storage<Integer> currentTerm;
    private final Storage<Integer> votedFor;
    private final Storage<ArrayList<Entry>> log;

    private int commitIndex;
    private int lastApplied;

    private final int[] nextIndex = new int[Constexpr.addresses.length];
    private final int[] matchIndex = new int[Constexpr.addresses.length];
    private final Linked<_AppendEntries> unconfirmed = new Linked<>();

    private Raft.State state;
    private final int id;
    private int leaderId;

    private final ScheduledThreadPoolExecutor timers = new ScheduledThreadPoolExecutor(1);
    private ScheduledFuture<?> future;
    private Consumer<String> apply;
    private int electionCheckCount;

    private final RUDPPeer peer;
    private final EventLoop eventLoop;

    public RUDPRaft(String directory, int id, RUDPPeer peer, EventLoop eventLoop) {
        this.peer = peer;
        this.id = id;
        this.eventLoop = eventLoop;

        currentTerm = new Storage<>(directory + "/currentTerm");
        votedFor = new Storage<>(directory + "/votedFor");
        log = new Storage<>(directory + "/log");
    }

    public void init(Consumer<String> apply) throws IOException {
        this.apply = apply;

        peer.register("raft", this);
        peer.register("appendEntries", appendEntriesFunc);
        peer.register("requestVote", requestVoteFunc);

        currentTerm.init(0);
        votedFor.init((Integer) null);
        var log = new ArrayList<Entry>();
        log.add(new Entry(0, ""));
        this.log.init(log);

        state = Raft.State.Follower;
        setAppendMiss();
    }

    private void toFollower() {
        future.cancel(false);
        state = Raft.State.Follower;
        setAppendMiss();
    }
    private void toCandidate() {
        future.cancel(false);
        state = Raft.State.Candidate;
        startElection();
    }
    private void toLeader() {
        future.cancel(false);
        state = Raft.State.Leader;
        Arrays.fill(nextIndex, log.get().size());
        Arrays.fill(matchIndex, 0);
        unconfirmed.clear();
        setAppend(0);
    }

    private void newTerm(int newTerm) throws IOException {
        currentTerm.set(newTerm);
        if (votedFor.get() != null) {
            votedFor.set(null);
        }
    }
    private void setAppendMiss() {
        future = timers.schedule(() -> eventLoop.run(this::toCandidate), appendMissDelay, TimeUnit.MILLISECONDS);
    }
    private void startElection() {
        eventLoop.run(() -> {
            if (state != Raft.State.Candidate) {
                return;
            }
            electionCheckCount = 0;
            try {
                newTerm(currentTerm.get() + 1);
                if (votedFor.get() != id) {
                    votedFor.set(id);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            try {
                var lastLogIndex = log.get().size() - 1;
                var lastLogTerm = log.get().get(lastLogIndex).term;
                var functor = requestVoteFunc.newFunctor(requestVoteSig, raftSig, currentTerm.get(), id, lastLogIndex, lastLogTerm);
                var rets = new Return[Constexpr.addresses.length];
                for (var _i = 0; _i < Constexpr.addresses.length; ++_i) {
                    if (id == _i) { continue; }
                    rets[_i] = peer.force(Constexpr.addresses[_i], functor);
                }
                setElectionCheck(rets, currentTerm.get());
            } catch (IOException e) {
                e.printStackTrace();
                toFollower();
            }

        });
    }
    private void setElectionCheck(Return[] rets, int term) {
        future = timers.scheduleAtFixedRate(() -> eventLoop.run(() -> {
            if (state != Raft.State.Candidate || term < currentTerm.get()) {
                return;
            }
            int tCount = 1;
            for (var _i = 0; _i < Constexpr.addresses.length; ++_i) {
                if (id == _i) { continue; }
                if (rets[_i].isSet()) {
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
        }), electionCheckPeriod, electionCheckPeriod, TimeUnit.MILLISECONDS);
    }
    static private record _AppendEntries(int id, Return data, int lastLogIndex, int nextLogIndex) { }
    private void setAppend(int delay) {
        future = timers.scheduleAtFixedRate(() -> eventLoop.run(() -> {
            if (state != Raft.State.Leader) {
                return;
            }
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
                        e.printStackTrace();
                    }

                    var clone = n;
                    n = n.right();
                    unconfirmed.split(clone);
                } else {
                    n = n.right();
                }
            }

            var clone = Arrays.copyOf(matchIndex, matchIndex.length);
            Arrays.sort(clone);
            commitIndex = Math.max(clone[(clone.length - 1) / 2], commitIndex);

            for (var _i = 0; _i < Constexpr.addresses.length; ++_i) {
                if (id == _i) { continue; }
                var prevLogIndex = nextIndex[_i] - 1;
                var prevLogTerm = log.get().get(prevLogIndex);
                Object[] entries;
                int _nextIndex;
                if (matchIndex[_i] == prevLogIndex) { _nextIndex = log.get().size(); }
                else { _nextIndex = Math.min(log.get().size(), nextIndex[_i] + 1); }
                entries = log.get().subList(nextIndex[_i], _nextIndex).toArray();
                nextIndex[_i] = _nextIndex;
                try {
                    var functor = appendEntriesFunc.newFunctor(appendEntriesSig, raftSig, currentTerm.get(), id, prevLogIndex, prevLogTerm, entries, commitIndex);
                    unconfirmed.addLast(new _AppendEntries(_i, peer.force(Constexpr.addresses[_i], functor), prevLogIndex, _nextIndex));
                } catch (IOException e) { e.printStackTrace(); }
            }
        }), delay, appendPeriod, TimeUnit.MILLISECONDS);
    }





    static private record AppendEntries(int term, boolean success) { }
    public AppendEntries appendEntries(int term, int leaderId, int prevLogIndex, int prevLogTerm, Entry[] entries, int leaderCommit) throws IOException {
        if (term < currentTerm.get()) {
            return new AppendEntries(currentTerm.get(), false);
        }

        future.cancel(false);
        this.leaderId = leaderId;
        if (term > currentTerm.get()) {
            newTerm(term);
        }
        if (state != Raft.State.Follower) {
            toFollower();
        }

        if (prevLogIndex >= log.get().size() || log.get().get(prevLogIndex).term != prevLogTerm) {
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

        setAppendMiss();
        return new AppendEntries(currentTerm.get(), true);
    }

    static private record RequestVote(int term, boolean voteGranted) { }
    public RequestVote requestVote(int term, int candidateId, int lastLogIndex, int lastLogTerm) throws IOException {
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
        }

        if (!Objects.equals(_votedFor, votedFor.get())) {
            votedFor.set(_votedFor);
        }

        return new RequestVote(currentTerm.get(), voteGranted);
    }
}
