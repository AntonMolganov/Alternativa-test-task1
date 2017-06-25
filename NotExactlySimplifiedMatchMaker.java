package alternativa.test.task1;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

class NotExactlySimplifiedMatchMaker implements MatchMaker{

    private final int matchPlayerCount;
    private final int minRank;
    private final int maxRank;
    private final int rankIncreaseTimeout;


    private final Deque<WaitingPlayer>[] waitingPlayers;
    private final OnMatchCreatedListener matchListener;
    private MatchSelectorThread selectorThread;

    /**
     * @param matchCreatedListener listener to run after each match creation
     */
    NotExactlySimplifiedMatchMaker(int matchPlayerCount, int matchPlayerMinRank, int matchPlayerMaxRank, int rankIncreaseTimeout, OnMatchCreatedListener matchCreatedListener) {
        this.matchPlayerCount = matchPlayerCount;
        this.minRank = matchPlayerMinRank;
        this.maxRank = matchPlayerMaxRank;
        this.rankIncreaseTimeout = rankIncreaseTimeout;
        this.matchListener = matchCreatedListener;

        //create different rank deque for all ranks
        waitingPlayers = new Deque[matchPlayerMaxRank - matchPlayerMinRank + 1];
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
        if (rank < minRank || rank > maxRank){
            throw new UnsupportedOperationException();
        }
        Deque<WaitingPlayer> d = waitingPlayers[rank- minRank];
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


    private boolean playersCompat(WaitingPlayer p1, WaitingPlayer p2, long time){
        if (p1 == p2) return true;
        if (p1.rank == p2.rank) return true;
        return (Math.abs(p1.rank - p2.rank)<=(time-p1.enterTime)/ rankIncreaseTimeout
                && Math.abs(p1.rank - p2.rank)<=(time-p2.enterTime)/ rankIncreaseTimeout);
    }


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
                        while (rankDeque.size() >= matchPlayerCount) {
                            for (int i = 0; i < matchPlayerCount; i++){
                                //only select players and release sync lock asap
                                selectedPlayers.add(rankDeque.pollLast());
                            }
                        }

                        //only players waited enough added to hard case processing
                        for (WaitingPlayer p : rankDeque){
                            if (now - p.enterTime >= rankIncreaseTimeout) playersLeft.add(p);
                        }

                    }

                    //create match in separate thread to avoid match creation delay in match selector thread
                    while (selectedPlayers.size() >= matchPlayerCount) {
                        WaitingPlayer[] matchMembers = new WaitingPlayer[matchPlayerCount];
                        for (int i = 0; i < matchPlayerCount; i++){
                            matchMembers[i] = selectedPlayers.remove();
                        }
                        new Thread(() -> createMatch(matchMembers)).start();
                    }

                }
                //simple cases done. no more than matchPlayerCount*rank count = 210 players left in all rank queues


                if (playersLeft.size() ==0) continue;


                //work around hard cases (i.e. players with different ranks)
                //normally do look over only once
                boolean doLookOver = true;
                while (doLookOver){
                    doLookOver = false;

                    //calc compatibility sets
                    int size = playersLeft.size();
                    Set<Integer>[] compatSets = new Set[size];

                    for (int i = 0; i < size; i++) compatSets[i] = new HashSet<>();

                    Set<Integer> suspects = new HashSet<>();
                    for (int i = 0; i < size; i++){
                        for ( int j = i; j < size; j++){
                            if (playersCompat(playersLeft.get(i), playersLeft.get(j), now)){
                                compatSets[i].add(j);
                                compatSets[j].add(i);
                            }
                        }
                        if (compatSets[i].size() >= matchPlayerCount) suspects.add(i);
                    }

                    //some logic, hard to make short explanation
                    Set<Integer> found = new HashSet<>();
                    found = find(suspects, compatSets, found);


                    //create match and do look over one more time if enough players found
                    if (found != null && found.size() >= matchPlayerCount) {

                        List<WaitingPlayer> matchMembers = new LinkedList<>();
                        found.stream().limit(matchPlayerCount).forEach(integer -> matchMembers.add(playersLeft.get(integer)));

                        for (WaitingPlayer player : matchMembers) {
                            playersLeft.remove(player);
                            waitingPlayers[player.rank - 1].remove(player);
                        }

                        new Thread(() -> createMatch(matchMembers.toArray(new WaitingPlayer[matchPlayerCount]))).start();

                        //do it one more time if some matched players found
                        //and break all look over cycles, they has no sense now because compatibility sets changed
                        doLookOver = true;
                    }
                }
            }
        }

        private Set<Integer> find(Set<Integer> suspects, Set<Integer>[] compatSets, Set<Integer> found) {
            for (Integer i : suspects) {
                if (found.contains(i)) continue;

                Set<Integer> s = compatSets[i];

                Set<Integer> intersection = new HashSet<>(suspects);
                intersection.retainAll(s);


                if (intersection.size() < matchPlayerCount) continue;

                Set<Integer> newFound = new HashSet<>(found);
                newFound.add(i);
                if (newFound.size() >= matchPlayerCount){
                    return newFound;
                }

                Set<Integer> result = find(intersection, compatSets, newFound);
                if (result != null && result.size() >= matchPlayerCount) return result;
            }
            return null;
        }



        void shutdown(){
            run = false;
        }
    }



}