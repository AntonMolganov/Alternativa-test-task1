package alternativa.test.task1;

import java.util.*;

public class Main {

    public static void main(String[] args) {

        final Random random = new Random();

        //generating available game players pool
        List<Player> availablePlayers = Collections.synchronizedList(new LinkedList<>());
        int PLAYERS_QTY = 1000000;

        for (int i = 0 ; i < PLAYERS_QTY; i++){

            //emulate real spreading - 1 rank is most, 30 rank is least
            //hardcoded spread borders for 30 ranks calculated outside
            final double[] spread_borders_30_ranks = {0.064516129,0.1268817204,0.1870967742,0.2451612903,0.3010752688,0.3548387097,0.4064516129,0.4559139785,0.5032258065,0.5483870968,0.5913978495,0.6322580645,0.6709677419,0.7075268817,0.7419354839,0.7741935484,0.8043010753,0.8322580645,0.8580645161,0.8817204301,0.9032258065,0.9225806452,0.9397849462,0.9548387097,0.9677419355,0.9784946237,0.9870967742,0.9935483871,0.9978494624,1};
            double d = Math.random();
            int rank = Player.MIN_RANK;
            for (int j = Player.MIN_RANK; j <= Player.MAX_RANK; j++){
                rank = j;
                if (d < spread_borders_30_ranks[j-1]) {
                    break;
                }
            }

            //otherwise simple spreading
            //int rank = Player.MIN_RANK + random.nextInt(Player.MAX_RANK);

            availablePlayers.add(new Player(i, rank));
        }


        SimplifiedMatchMaker matchMaker = new SimplifiedMatchMaker((Player... players) -> {

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
        });
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
