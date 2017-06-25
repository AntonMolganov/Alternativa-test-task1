package alternativa.test.task1;


public interface MatchMaker {
    void registerPlayer(int uid, int rank);
    void startMatchMaking();
    void stopMatchMaking();

    class WaitingPlayer extends Player{
        final long enterTime;
        WaitingPlayer(int uid, int rank, long enterTime) {
            super(uid,rank);
            this.enterTime = enterTime;
        }
    }

    interface OnMatchCreatedListener {
        void onMatchCreated(Player... players);
    }

}
