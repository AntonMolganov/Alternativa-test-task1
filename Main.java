package alternativa.test.task1;

import java.util.*;

public class Main {

    public static void main(String[] args) {

        final Random random = new Random();

        //generating available game players pool
        int MATCH_PLAYERS_COUNT = 8;
        int MATCH_PLAYER_MIN_RANK = 1;
        int MATCH_PLAYER_MAX_RANK = 30;
        int RANK_INCREASE_TIMEOUT = 5000;
        List<Player> availablePlayers = Collections.synchronizedList(new LinkedList<>());
        int PLAYERS_QTY = 1000000;


        //emulate real spreading - 1 rank is most, 30 rank is least
        int rank_count = MATCH_PLAYER_MAX_RANK-MATCH_PLAYER_MIN_RANK+1;
        double rank_sum = rank_count*(MATCH_PLAYER_MAX_RANK+MATCH_PLAYER_MIN_RANK)/2f;
        double[] spread_borders= new double[rank_count];
        double next_border_sum = 0;
        for (int i=0; i<spread_borders.length;i++){
            spread_borders[i] = next_border_sum + (MATCH_PLAYER_MAX_RANK-i+MATCH_PLAYER_MIN_RANK-1)/rank_sum;
            next_border_sum = spread_borders[i];
        }


        for (int i = 0 ; i < PLAYERS_QTY; i++){

            //emulate real spreading - 1 rank is most, 30 rank is least
            double d = Math.random();
            int rank = MATCH_PLAYER_MIN_RANK;
            for (int j = MATCH_PLAYER_MIN_RANK; j <= MATCH_PLAYER_MAX_RANK; j++){
                rank = j;
                if (d < spread_borders[j-1]) {
                    break;
                }
            }

            //otherwise simple spreading
            //int rank = Player.MIN_RANK + random.nextInt(Player.MAX_RANK);

            availablePlayers.add(new Player(i, rank));
        }


        MatchMaker.OnMatchCreatedListener listener = players -> {
            //            Log in nice format
//            StringBuilder sb = new StringBuilder("Match created, ");
//            sb.append("time:").append(System.currentTimeMillis());
//            for (Player p : players){
//                sb.append("\nuid:").append(p.uid).append(" rank:").append(p.rank);
//            }
//            sb.append("\n");
//            System.out.println(sb);


//            Log as required by task doc
            StringBuilder sb = new StringBuilder("(");
            sb.append(System.currentTimeMillis());
            for (Player p : players){
                sb.append(" ").append(p.uid);
            }
            sb.append(")");
            System.out.println(sb);


            //return match ended players to availablePlayers pool (i.e. match is done immediately)
            for (Player p : players){
                availablePlayers.add(new Player(p.uid, p.rank));
            }
        };

//        MatchMaker matchMaker = new SimplifiedMatchMaker(listener);
        MatchMaker matchMaker = new NotExactlySimplifiedMatchMaker(MATCH_PLAYERS_COUNT, MATCH_PLAYER_MIN_RANK, MATCH_PLAYER_MAX_RANK, RANK_INCREASE_TIMEOUT, listener);
        matchMaker.startMatchMaking();


        //emulate players connecting to game
        class LoadEmulatorThread extends Thread{
            private volatile boolean run = true;
            @Override
            public void run() {
                setName("Load emulator");
                while (run){
                    try {
                        int randomPosition = random.nextInt(availablePlayers.size());
                        Player p = availablePlayers.remove(randomPosition);
                        matchMaker.registerPlayer(p.uid, p.rank);
                    }catch (IllegalArgumentException e){
                        //all players already in matches
                        System.out.println("No players available");
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e1) {
                            e1.printStackTrace();
                        }
                    }

                    //emulate players connection interval
                    long MIN_TIME = 100; //millis
                    long MAX_TIME = 500; //millis
                    long sleepTime = MIN_TIME + (long) (Math.random()*(MAX_TIME-MIN_TIME));
                    try {
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }

            public void shutdown(){
                run = false;
            }
        }

        //starting 10 simultaneous player registration threads
        for (int i = 0; i < 10; i++) new LoadEmulatorThread().start();
    }

}
