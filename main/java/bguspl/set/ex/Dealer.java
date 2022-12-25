package bguspl.set.ex;

import bguspl.set.Env;


import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * Participating players
     */
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    /*final*/ List<Integer> deck; // not final for tests

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = 0;

    // added fields
    /**
     * Queue of player's who completed a SET, player's in queue are in order of completion
     */
    public BlockingQueue<Integer> playersToCheck; // public for tests

    /**
     * Dealer thread
     */
    protected Thread dealerThread;

    /**
     * The starting time of the game
     */
    protected long start_time;

    /**
     * The current time all-along the simulation of the game
     */
    protected long curr_time;

    /**
     * Random list of indices of table for removing cards randomly in removeAllCardsFromTable() method
     */
    protected List<Integer> randomRemovalOfCards;

    /**
     * An array of all player threads. for gracefully finale
     */
    public Thread[] playersThreads;

    /**
     * Indicator if after dealer.placeCardsOnTable() not all the slots on table are occupied with a card
     */
    public volatile boolean tableHaveEmptySlots;

    /**
     * Dealer's lock
     */
    private final Object dealerLock;
 
    /**
     * When placing cards back on table, each time dealer tells random player to wake all players
     */
    private Random rand_player_to_wake;

    /**
     * SET_SIZE as defined in config
     */
    public final int SET_SIZE;

    /**
     * Dealer wait time if there is no player inserting a SET to be checked
     */
    public final int TIMEOUT = 50;



    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());

        // added to CTR
        playersToCheck = new ArrayBlockingQueue<Integer>(env.config.players);
        start_time = env.config.turnTimeoutMillis;
        curr_time = start_time;
        randomRemovalOfCards = new ArrayList<>();
        for (int i = 0; i < table.slotToCard.length; ++i)
            randomRemovalOfCards.add(i);
        playersThreads = new Thread[env.config.players];
        tableHaveEmptySlots = false;
        dealerLock = new Object();
        rand_player_to_wake = new Random();
        SET_SIZE = env.config.featureSize;
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        dealerThread = Thread.currentThread();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");

        System.out.println("Starting in dealer.run()");
        // initializing players threads
        for (int i = 0; i < players.length; ++i) {
            if (!players[i].isHuman()) {
                System.out.printf("player %s is AI",players[i].id);
                System.out.println();
            }
            Thread threadPlayer = new Thread(players[i], "Player - " + i);
            playersThreads[i] = threadPlayer;
            threadPlayer.start();
        }

        while (!shouldFinish()) {

            try {
                Collections.shuffle(deck);
                env.ui.setCountdown(curr_time, false);
                updateTimerDisplay(false);
                placeCardsOnTable();

                timerLoop();

                updateTimerDisplay(false); // check if timer goes all the way down to 0 or 0.1 ms
                removeAllCardsFromTable();
                curr_time = start_time + 999;
            } catch (InterruptedException e) {System.out.println("dealer was caught here, end of while(!terminate)"); break;}

        }

        if (!terminate) { // we left while(!shouldFinish()) because there were no SETs left in deck, it means terminate == true
            announceWinners();
            terminate();
        }

        System.out.println("SET simulation has ended :)");
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
        System.out.println("Thread dealer has been terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() throws InterruptedException{
        while (!terminate && curr_time > reshuffleTime) {
            sleepUntilWokenOrTimeout();
            if (env.config.hints)
                table.hints();
            if (!playersToCheck.isEmpty())
                checkSet();
            updateTimerDisplay(false);
        }
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        // TODO implement

        // reverse loop (from the end of the array to the start of it) on players: player[i].terminate()
        // dealerThread need to be shutdown
        terminate = true;
        for (int i = playersThreads.length-1; i >= 0; --i) {
            players[i].terminate();
            playersThreads[i].interrupt();
            try {
                players[i]._notifyAll();
                playersThreads[i].join();
            } catch (InterruptedException ignored) {  }
        }

        table.gameOn();
        dealerThread.interrupt();
        _interrupt();
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checking SET of player's who declared SET
     */
    private void checkSet() throws InterruptedException{

        PlayerState state = PlayerState.Playing;

        while (!playersToCheck.isEmpty()) {
            int playerId = playersToCheck.remove();
            // making a copy of player's SET
            ArrayList<Integer> toRemove = (ArrayList<Integer>) players[playerId].clone();
            //System.out.println("player " + playerId + " SET is: " + toRemove);

            if (toRemove.size() < 3) {
                players[playerId].setPlayerState(PlayerState.Playing);
                players[playerId].setSetState(SetState.NotComplete);
            }
            else if ((toRemove.size() == 3)) {

                if (env.util.testSet(getSetOfCards(toRemove))) {

                    // set player && set state
                    state = PlayerState.Playing;
                    players[playerId].setSetState(SetState.Legal);

                    // from now on table is BLOCKED for everyone
                    table.gameOff();

                    // remove tokens from slots
                    for (Player player : players)
                        player.removeTokensFromSlots(toRemove);

                    // remove cards from slots - from now on table is BLOCKED for everyone
                    removeCardsFromTable(toRemove);

                    //System.out.println("after validating SET is legal, my SET size is 0? " + (players[playerId].getMySet().size() == 0));

                    // place cards on table again and then release the TABLE
                    placeCardsOnTable();

                    // reset game clock
                    curr_time = start_time + 999;
                }
                else { // SET of player is Illegal
                    state = PlayerState.PlayingAfterPunishment;
                    players[playerId].setSetState(SetState.Illegal);
                }

            }

            players[playerId]._notify(state);
        }
    }

    /**
     * Checks if any cards should be removed from the table and returns them to the deck.
     */
    private void removeCardsFromTable(List<Integer> toRemove) {
        // TODO implement
        for (Integer slot : toRemove)
            table.removeCard(slot);
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     * pre - deck is shuffled //added by me
     */
    void placeCardsOnTable() throws InterruptedException {
        // TODO implement
        // 1. shuffle the indices of the empty slots
        Collections.shuffle(table.emptySlots);
        // 2. in each index we extract we place a card from the shuffled 'deck'
        int curr_size = table.emptySlots.size();
        for (int i = 0; i < curr_size; ++i) {
            if (deck.size() > 0) {
                int empty_slot = table.emptySlots.remove(0);
                int card_to_place = deck.remove(0);
                table.placeCard(card_to_place, empty_slot);
            }
        }

        tableHaveEmptySlots = (table.emptySlots.size() != 0);
        //System.out.println("there is empty slots of table = " + tableHaveEmptySlots + " empty slots on table are: " + table.emptySlots);
        if (tableHaveEmptySlots) { // It means that from now on there aren't enough cards to place on the whole table, thus we'll remove this empty slots from player's 'slotsToPlace'
            for (Player player : players) {
                for (Integer emptySlot : table.emptySlots) {
                    player.slotsToPlace.remove((Object) emptySlot);
                    table.removeTokens(emptySlot);
                }
            }
        }

        // activate Table
        table.gameOn();
        // notify players to wake-up they can resume playing
        table.wakeUp();

        players[rand_player_to_wake.nextInt(players.length)]._notifyAll();
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        // TODO implement
        /*
            Dealer sleeps for iterations of 1 second
            Every second he wakes up and update Timer to be 1 second less
               ** Unless he is woken up by a Player that have completed a set and was inserted to his playersToCheck queue
         */
        synchronized (dealerLock) {
            try {
                //System.out.println("Dealer is asleep");
                dealerLock.wait(TIMEOUT);
                //System.out.println("Dealer is awake !");
            } catch (InterruptedException e) {dealerThread.interrupt();  dealerThread.interrupted();}
        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        // TODO implement
        if (curr_time <= env.config.turnTimeoutWarningMillis) {
            //System.out.println("curr_time in ms: " + curr_time);
            env.ui.setCountdown(curr_time, true);
            curr_time -= TIMEOUT;
        }
        else {
            env.ui.setCountdown(curr_time, false);
            curr_time -= TIMEOUT;
        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        // TODO implement
        table.gameOff();

        Collections.shuffle(randomRemovalOfCards);
        for (Integer rand_slot : randomRemovalOfCards) {
            Integer card = table.slotToCard[rand_slot];
            if (card != null) {
//                for (int playerId = 0; playerId < table.tokenToSlot.get(rand_slot).size(); ++playerId)
//                    players[table.tokenToSlot.get(rand_slot).get(playerId)].handleTokenActions(rand_slot);

                table.removeCard(rand_slot);
                deck.add(card);
            }
        }

        resetGame();
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        // TODO implement
        Integer maxId = 0;
        Integer maxScore = players[0].score();
        List<Integer> winners_list = new ArrayList<>();
        List<Integer> scores_list = new ArrayList<>();
        for (int j = 1; j < players.length; ++j) {
            if (players[j].score() > maxScore) {
                maxId = j;
                maxScore = players[j].score();
            }
        }
        winners_list.add(maxId);
        scores_list.add(maxScore);
        for (Player player : players) {
            if (player.score() == maxScore && player.id != maxId) {
                winners_list.add(player.id);
                scores_list.add(player.score());
            }
        }

        int[] winners = new int[winners_list.size()];
        for (int p = 0; p < winners.length; ++p)
            winners[p] = winners_list.get(p);

        System.out.println("winners are: " + winners_list + " with score: " + scores_list);
        env.ui.announceWinner(winners);
    }

    // added methods
    /**
     * Add player id to the queue   - means nPlayer has completed a SET and awaits the dealer to check it
     */
    public void addPlayerToQueue(int nPlayer) { // need to be synchronized ?
        playersToCheck.offer(nPlayer);
    }

    /**
     *
     //* @param player - the player id
     * @param toClone - the player cloned list of slots where his 3 tokens where placed to make a SET
     * @return player's set of cards
     */
    public int[] getSetOfCards(List<Integer> toClone) {
        int[] cards = new int[3];
        //System.out.println("SET of cards to be check is - " + toClone);

        for (int i = 0; i < toClone.size(); ++i) {
            if (toClone.get(i) != null && table.slotToCard[toClone.get(i)] != null)
                cards[i] = table.slotToCard[toClone.get(i)];
        }

        return cards;
    }

    public void resetGame() {

        env.ui.removeTokens();

        // Resetting all players states: set_state && player_state && player.mySet && table.playersSets for each player
        for (Player player : players) {
            player.setPlayerState(PlayerState.Playing);
            player.setSetState(SetState.NotComplete);
            player.clearPlayerSet();
        }

        // Clearing queue of player's SET to be checked
        playersToCheck = new ArrayBlockingQueue<Integer>(players.length);

    }

    private List<Integer> checkPlayers(int slot) {

        List<Integer> ans = new ArrayList<>();
        for (Player player : players) {
            if (player.getMySet().contains(slot))
                ans.add(player.id);
        }

        return ans;
    }

    public synchronized void wakeUp() {
        notify();
    }

    public void _interrupt() {

        synchronized (dealerLock) {
            dealerLock.notifyAll();
        }

    }

    

}
