package alternativa.test.task1;

class Player{
    public final static int MIN_RANK = 1;
    public final static int MAX_RANK = 30;

    public final int uid;
    public final int rank;
    public Player(int uid, int rank) {
        this.uid = uid;
        this.rank = rank;
    }
}