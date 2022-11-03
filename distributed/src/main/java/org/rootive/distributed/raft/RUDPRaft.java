package org.rootive.distributed.raft;

import org.rootive.nio.EventLoop;
import org.rootive.nio_rpc.RUDPPeer;
import org.rootive.rpc.Gap;
import org.rootive.rpc.Literal;
import org.rootive.rpc.Signature;
import org.rootive.rpc.client.*;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class RUDPRaft {
    public record Entry(int term, byte[] log) { }
    static private final int appendPeriod = 2000;
    static private final int missDelay = 4000;
    static private final int electionCheckPeriodMin = 200;
    static private final int electionCheckPeriodMax = 600;
    static private final int electionCheckLine = 8;

    static private final Signature raftSignature = new Signature("raft", "object");
    static private final Signature appendEntriesSignature = new Signature("raft", "appendEntries");
    static private final Signature requestVoteSignature = new Signature("raft", "requestVote");

    private final Storage<Integer> currentTerm;
    private final Storage<Integer> votedFor;
    private final StorageList<Entry> log;

    private int commitIndex;
    private int lastApplied;

    private final int[] nextIndex = new int[Constexpr.addresses.length];
    private final int[] matchIndex = new int[Constexpr.addresses.length];
    private final Linked<_AppendEntries> unconfirmed = new Linked<>();
    private final AtomicInteger state = new AtomicInteger();
    private final int id;
    private int leaderID = -2;
    private final Random rand = new Random();

    private final ScheduledThreadPoolExecutor timers = new ScheduledThreadPoolExecutor(1);
    private ScheduledFuture<?> future;
    private Consumer<byte[]> apply;
    private int electionCheckCount;

    private final RUDPPeer peer;
    private final EventLoop eventLoop;

    public RUDPRaft(RUDPPeer peer, EventLoop eventLoop, int id, String directory) {
        this.peer = peer;
        this.eventLoop = eventLoop;
        this.id = id;

        new File(directory).mkdirs();
        currentTerm = new Storage<>(directory + "/currentTerm");
        votedFor = new Storage<>(directory + "/votedFor");
        log = new StorageList<>(directory + "/log");
    }

    public EventLoop getEventLoop() {
        return eventLoop;
    }
    public int getID() {
        return id;
    }
    public Raft.State getState() {
        return Raft.State.values()[state.get()];
    }

    public void init(Consumer<byte[]> apply) throws IOException, NoSuchMethodException {
        this.apply = apply;

        peer.register("raft", "object", this);
        peer.register("raft", "appendEntries", RUDPRaft.class.getMethod("appendEntries", int.class, int.class, int.class, int.class, Entry[].class, int.class));
        peer.register("raft", "requestVote", RUDPRaft.class.getMethod("requestVote", int.class, int.class, int.class, int.class));

        currentTerm.init(Integer.class, () -> 0);
        votedFor.init(Integer.class, () -> null);
        log.init(Entry.class, () -> {
            var log = new ArrayList<Entry>();
            log.add(new Entry(0, "".getBytes()));
            return log;
        });
        System.out.println("saved log count " + log.get().size());

        state.set(Raft.State.Follower.ordinal());
        setMiss();
    }

    private void toFollower() {
        System.out.println("to follower");
        future.cancel(false);
        state.set(Raft.State.Follower.ordinal());
        setMiss();
    }
    private void toCandidate() {
        System.out.println("to candidate");
        future.cancel(false);
        state.set(Raft.State.Candidate.ordinal());
        startElection();
    }
    private void toLeader() {
        System.out.println("to leader");
        future.cancel(false);
        leaderID = id;
        state.set(Raft.State.Leader.ordinal());
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
        future = timers.schedule(() -> eventLoop.run(this::toCandidate), missDelay, TimeUnit.MILLISECONDS);
    }
    private void startElection() {
        System.out.println("start election");
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
                final ClientStub.Session session = new ClientStub.Session();
                rets[_i] = session.literal(RequestVote.class,
                        requestVoteSignature,
                        raftSignature,
                        new Literal(currentTerm.get()),
                        new Literal(id),
                        new Literal(lastLogIndex),
                        new Literal(lastLogTerm));
                peer.force(Constexpr.addresses[_i], session, Gap.SINGLE);
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
    private void setElectionCheck(Return<?>[] rets, int term) {
        var period = getElectionCheckPeriod();
        future = timers.scheduleAtFixedRate(() -> eventLoop.run(() -> {
            if (state.get() != Raft.State.Candidate.ordinal() || term < currentTerm.get()) {
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

    private record _AppendEntries(int id, Return<AppendEntries> data, int lastLogIndex, int nextLogIndex) { }
    private void setAppend() {
        future = timers.scheduleAtFixedRate(() -> eventLoop.run(() -> {
            if (state.get() != Raft.State.Leader.ordinal()) {
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
                    unconfirmed.escape(clone);
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
                    final ClientStub.Session session = new ClientStub.Session();
                    final Return<AppendEntries> ret = session.literal(AppendEntries.class,
                            appendEntriesSignature,
                            raftSignature,
                            new Literal(currentTerm.get()),
                            new Literal(id),
                            new Literal(prevLogIndex),
                            new Literal(prevLogTerm),
                            new Literal(entries),
                            new Literal(commitIndex));
                    peer.force(Constexpr.addresses[_i], session, Gap.SINGLE);

                    unconfirmed.addLast(new _AppendEntries(_i, ret, prevLogIndex, _nextIndex));
                } catch (IOException e) { e.printStackTrace(); }
            }

            while (commitIndex > lastApplied) {
                apply.accept(log.get().get(++lastApplied).log);
            }
        }), 0, appendPeriod, TimeUnit.MILLISECONDS);
    }



    private record AppendEntries(int term, boolean success) { }
    public AppendEntries appendEntries(int term, int leaderId, int prevLogIndex, int prevLogTerm, Entry[] entries, int leaderCommit) throws IOException {
        if (term < currentTerm.get()) {
            return new AppendEntries(currentTerm.get(), false);
        }

        System.out.println("appended by " + leaderId);
        future.cancel(false);
        this.leaderID = leaderId;
        if (term > currentTerm.get()) {
            newTerm(term);
        }
        if (state.get() != Raft.State.Follower.ordinal()) {
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

    private record RequestVote(int term, boolean voteGranted) { }
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
