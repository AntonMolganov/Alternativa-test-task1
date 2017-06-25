package alternativa.test.task1;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;


class SimplifiedMatchMaker implements MatchMaker {

    private final static int MATCH_PLAYERS_COUNT = 8;
    private final static int OTHER_RANKS_ALLOW_TIMEOUT = 5000;
    private final static int MIN_RANK = 1;
    private final static int MAX_RANK = 30;



    private final Deque<WaitingPlayer>[] waitingPlayers;
    private final OnMatchCreatedListener matchListener;
    private MatchSelectorThread selectorThread;


    /**
     * @param matchCreatedListener listener to run after each match creation
     */
    SimplifiedMatchMaker(OnMatchCreatedListener matchCreatedListener) {
        this.matchListener = matchCreatedListener;

        //create different rank deque for all ranks
        waitingPlayers = new Deque[MAX_RANK - MIN_RANK + 1];
        for (int i = 0; i < waitingPlayers.length; i++){
            waitingPlayers[i] = new ConcurrentLinkedDeque<>();
        }
    }


    public void startMatchMaking(){
        if (selectorThread != null) selectorThread.shutdown();
        selectorThread = new MatchSelectorThread();
        selectorThread.start();
    }


    public void stopMatchMaking(){
        if (selectorThread != null) selectorThread.shutdown();
    }


    public void registerPlayer(int uid, int rank){
        Deque<WaitingPlayer> d = waitingPlayers[rank-MIN_RANK];
        synchronized (d){
            d.add(new WaitingPlayer(uid,rank, System.currentTimeMillis()));
        }
    }


    private void createMatch(WaitingPlayer... players){
        //emulate match creation delay
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (matchListener != null) matchListener.onMatchCreated(players);
    }


    private static boolean playersCompat(WaitingPlayer p1, WaitingPlayer p2, long time){
        if (p1 == p2) return true;
        if (p1.rank == p2.rank) return true;
        return (Math.abs(p1.rank - p2.rank)<=(time-p1.enterTime)/ OTHER_RANKS_ALLOW_TIMEOUT
                && Math.abs(p1.rank - p2.rank)<=(time-p2.enterTime)/ OTHER_RANKS_ALLOW_TIMEOUT);
    }

    final static Comparator<WaitingPlayer> byWaitTimeReverse = (o1, o2) -> {
        if (o1.enterTime > o2.enterTime) return 1;
        if (o1.enterTime < o2.enterTime) return -1;
        return 0;
    };

    private class MatchSelectorThread extends Thread{
        private volatile boolean run = true;
        @Override
        public void run() {
            setName("Match selector");

            while (run){
                //simple cases processing (i.e. create matches with same rank)
                //not matched players added to this list to process hard cases next (different ranks)
                List<WaitingPlayer> playersLeft = new LinkedList<>();

                long now = System.currentTimeMillis();

                for (Deque<WaitingPlayer> rankDeque : waitingPlayers) {
                    LinkedList<WaitingPlayer> selectedPlayers = new LinkedList<>();
                    synchronized (rankDeque) {
                        while (rankDeque.size() >= MATCH_PLAYERS_COUNT) {
                            for (int i = 0; i < MATCH_PLAYERS_COUNT; i++){
                                //only select players and release sync lock asap
                                selectedPlayers.add(rankDeque.pollLast());
                            }
                        }

                        //only players waited enough added to hard case processing
                        for (WaitingPlayer p : rankDeque){
                            if (now - p.enterTime >= OTHER_RANKS_ALLOW_TIMEOUT) playersLeft.add(p);
                        }

                    }

                    //create match in separate thread to avoid match creation delay in match selector thread
                    while (selectedPlayers.size() >= MATCH_PLAYERS_COUNT) {
                        WaitingPlayer[] matchMembers = new WaitingPlayer[MATCH_PLAYERS_COUNT];
                        for (int i = 0; i < MATCH_PLAYERS_COUNT; i++){
                            matchMembers[i] = selectedPlayers.remove();
                        }
                        new Thread(() -> createMatch(matchMembers)).start();
                    }

                }
                //simple cases done. no more than 7*30 = 210 players left in all rank queues



                //work around hard cases (i.e. players with different ranks)
                //sorting to make better wait times
                playersLeft.sort(byWaitTimeReverse);

                //normally do look over only once
                boolean doLookOver = true;
                while (doLookOver){
                    doLookOver = false;

                    //calc compatibility sets
                    int size = playersLeft.size();
                    Set<Integer>[] compatSets = new Set[size];

                    for (int i = 0; i < size; i++) compatSets[i] = new HashSet<>();

                    for (int i = 0; i < size; i++){
                        for ( int j = i; j < size; j++){
                            if (playersCompat(playersLeft.get(i), playersLeft.get(j), now)){
                                compatSets[i].add(j);
                                compatSets[j].add(i);
                            }
                        }
                    }

                    //some big logic, hard to make short explanation
                    for (int i1 = 0; i1 < size; i1 ++){
                        Set<Integer> s1 = compatSets[i1];
                        if (s1.size() < MATCH_PLAYERS_COUNT) continue;



                        for (Integer i2 : s1){
                            if (Objects.equals(i2,i1)) continue;

                            Set<Integer> s2 = compatSets[i2];
                            if (s2.size() < MATCH_PLAYERS_COUNT) continue;

                            Set<Integer> intersection1 = new HashSet<>(s1);
                            intersection1.retainAll(s2);

                            if (intersection1.size() < MATCH_PLAYERS_COUNT) continue;



                            for (Integer i3 : intersection1){
                                if (Objects.equals(i3,i1)
                                        || Objects.equals(i3,i2)) continue;

                                Set<Integer> s3 = compatSets[i3];
                                if (s3.size() < MATCH_PLAYERS_COUNT) continue;

                                Set<Integer> intersection2 = new HashSet<>(intersection1);
                                intersection2.retainAll(s3);

                                if (intersection2.size() < MATCH_PLAYERS_COUNT) continue;



                                for (Integer i4 : intersection2){
                                    if (Objects.equals(i4,i1)
                                            || Objects.equals(i4,i2)
                                            || Objects.equals(i4,i3)) continue;

                                    Set<Integer> s4 = compatSets[i4];
                                    if (s4.size() < MATCH_PLAYERS_COUNT) continue;

                                    Set<Integer> intersection3 = new HashSet<>(intersection2);
                                    intersection3.retainAll(s4);

                                    if (intersection3.size() < MATCH_PLAYERS_COUNT) continue;



                                    for (Integer i5 : intersection3){
                                        if (Objects.equals(i5,i1)
                                                || Objects.equals(i5,i2)
                                                || Objects.equals(i5,i3)
                                                || Objects.equals(i5,i4)) continue;

                                        Set<Integer> s5 = compatSets[i5];
                                        if (s5.size() < MATCH_PLAYERS_COUNT) continue;

                                        Set<Integer> intersection4 = new HashSet<>(intersection3);
                                        intersection4.retainAll(s5);

                                        if (intersection4.size() < MATCH_PLAYERS_COUNT) continue;



                                        for (Integer i6 : intersection4){
                                            if (Objects.equals(i6,i1)
                                                    || Objects.equals(i6,i2)
                                                    || Objects.equals(i6,i3)
                                                    || Objects.equals(i6,i4)
                                                    || Objects.equals(i6,i5)) continue;

                                            Set<Integer> s6 = compatSets[i6];
                                            if (s6.size() < MATCH_PLAYERS_COUNT) continue;

                                            Set<Integer> intersection5 = new HashSet<>(intersection4);
                                            intersection5.retainAll(s6);

                                            if (intersection5.size() < MATCH_PLAYERS_COUNT) continue;



                                            for (Integer i7 : intersection5){
                                                if (Objects.equals(i7,i1)
                                                        || Objects.equals(i7,i2)
                                                        || Objects.equals(i7,i3)
                                                        || Objects.equals(i7,i4)
                                                        || Objects.equals(i7,i5)
                                                        || Objects.equals(i7,i6)) continue;

                                                Set<Integer> s7 = compatSets[i7];
                                                if (s7.size() < MATCH_PLAYERS_COUNT) continue;

                                                Set<Integer> intersection6 = new HashSet<>(intersection5);
                                                intersection6.retainAll(s7);

                                                if (intersection6.size() < MATCH_PLAYERS_COUNT) continue;



                                                for (Integer i8 : intersection6){
                                                    if (Objects.equals(i8,i1)
                                                            || Objects.equals(i8,i2)
                                                            || Objects.equals(i8,i3)
                                                            || Objects.equals(i8,i4)
                                                            || Objects.equals(i8,i5)
                                                            || Objects.equals(i8,i6)
                                                            || Objects.equals(i8,i7)) continue;

                                                    Set<Integer> s8 = compatSets[i8];
                                                    if (s8.size() < MATCH_PLAYERS_COUNT) continue;

                                                    Set<Integer> intersection7 = new HashSet<>(intersection6);
                                                    intersection7.retainAll(s8);

                                                    if (intersection7.size() < MATCH_PLAYERS_COUNT) continue;

                                                    WaitingPlayer[] matchMembers = new WaitingPlayer[MATCH_PLAYERS_COUNT];
                                                    matchMembers[0] = playersLeft.get(i1);
                                                    matchMembers[1] = playersLeft.get(i2);
                                                    matchMembers[2] = playersLeft.get(i3);
                                                    matchMembers[3] = playersLeft.get(i4);
                                                    matchMembers[4] = playersLeft.get(i5);
                                                    matchMembers[5] = playersLeft.get(i6);
                                                    matchMembers[6] = playersLeft.get(i7);
                                                    matchMembers[7] = playersLeft.get(i8);

                                                    for (WaitingPlayer player : matchMembers) {
                                                        playersLeft.remove(player);
                                                        waitingPlayers[player.rank - 1].remove(player);
                                                    }


                                                    //create match in separate thread to avoid match creation delay in current thread
                                                    new Thread(() -> createMatch(matchMembers)).start();


                                                    //do it one more time if some matched players found
                                                    //and break all look over cycles, they has no sense now because compatibility sets changed
                                                    doLookOver = true;
                                                    break;
                                                }
                                                if (doLookOver) break;
                                            }
                                            if (doLookOver) break;
                                        }
                                        if (doLookOver) break;
                                    }
                                    if (doLookOver) break;
                                }
                                if (doLookOver) break;
                            }
                            if (doLookOver) break;
                        }
                        if (doLookOver) break;
                    }
                }
            }
        }

        void shutdown(){
            run = false;
        }
    }





}