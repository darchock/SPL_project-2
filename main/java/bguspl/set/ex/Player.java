package bguspl.set.ex;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
// import java.util.concurrent.locks.ReentrantLock;
// import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

import bguspl.set.Env;
//import com.sun.tools.javac.util.Pair;

import java.util.*;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score = 0;

            // added fields //

    /**
     * Queue of key-presses; consists from slots that player (AI OR human) have clicked
     */
    public /*final */ BlockingQueue<Integer> queueOfActions; // not final for tests

    /**
     * The slot's in Table that player hasn't placed a token on yet
     */
    public List<Integer> slotsToPlace;

    /**
     * The dealer that runs the program, we need access to his Thread to notify him
     */
    public Dealer dealer;

    /**
     * Player's state
     */
    public volatile PlayerState state; // public for tests

    /**
     * Player's SET; consist from the slot he has placed tokens on
     */
    List<Integer> mySet;

    /**
     * The player's SET state: Legal OR Illegal:
     * The player accordingly will address Player.point() method Or Player.penalty() method respectively
     */
    protected volatile SetState set_state; // protected for tests

    /**
     * Random for AI key-press generator
     */
    private Random rand_generator;

    /**
     * Lock-Object player is grabbing on while waiting for dealer to check his SET
     */
    private static final Object obj = new Object();

    /**
     * Penalty time Intervals for human players
     */
    private final int FREEZE_PENALTY_INTERVAL = 1000;

    public ReentrantLock playersQueueLock;



    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        terminate = false;

        // added to CTR
        queueOfActions = new ArrayBlockingQueue<Integer>(env.config.featureSize, true);
        slotsToPlace = new ArrayList<>();
        for (int i = 0; i < env.config.tableSize; ++i)
            slotsToPlace.add(i);
        this.dealer = dealer;
        state = PlayerState.Playing;
        mySet = new ArrayList<>();
        set_state = SetState.NotComplete;
        rand_generator = new Random();

        playersQueueLock = new ReentrantLock(true);

    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + "starting.");
        System.out.println("Thread" + Thread.currentThread().getName() + " starting.");
        if (!human) {
            createArtificialIntelligence();
            System.out.println("player thread is after createArtificialIntelligence()");
        }

        while (!terminate) {
            //TODO implement main player loop
            while (!terminate && !table.canPlay()) {
                try {
                    //System.out.println("table isn't allowing us to play");
                    table._wait();
                    //System.out.println("we got a wakeUp() call, we can resume playing");
                } catch (InterruptedException e) {
                    System.out.printf("Thread player %s has been shutdown due to closing game window - in run() waiting for table", id);
                    System.out.println(); table.wakeUp(); break;
                }
            }

            synchronized (queueOfActions) {
                while (!terminate && queueOfActions.isEmpty()) {
                    try {
                        _wait();
                    } catch (InterruptedException e) { table.wakeUp(); playerThread.interrupted(); break; }
                }

                try {
                    while (!terminate && !queueOfActions.isEmpty()) { // an action was inserted by keyPressed()
                        //System.out.println("queueOfActions isn't empty");

                        Integer slot = queueOfActions.poll();
                        handleTokenActions(slot);

                        if (mySet.size() == 3) {
                            if (state == PlayerState.Playing) {
                                //System.out.printf("player %s has declared a SET", id);

                                dealer.addPlayerToQueue(id); // add player to dealer's queue for his SET to be checked
                                dealer.wakeUp(); // notify dealer that a SET has inserted to his queue and need to be checked
                                state = PlayerState.Waiting; // wait to be checked

                                try {
                                    playersQueueLock.lock();
                                    synchronized (obj) {
                                        while (state == PlayerState.Waiting) {
                                            obj.wait();
                                        }
                                    }
                                } catch (Exception e ) {System.out.printf("player %s is stuck at queue lock",id); System.out.println();
                                        table.wakeUp(); break;}
                                finally {
                                    playersQueueLock.unlock();
                                }


                                // Checking player.set_state to direct player to his rightful outcome
                                if (set_state == SetState.Legal) {
                                    //System.out.printf("player %s is legal", id); //System.out.println();
                                    point();
                                    //System.out.println("after point(), the slots we have to choose from are - " + slotsToPlace);
                                } else if (set_state == SetState.Illegal) {
                                    //System.out.printf("player %s is illegal", id); //System.out.println();
                                    penalty();
                                } else { // set_state == SetState.NotComplete
                                    //System.out.printf("actually player %s hasn't completed a set", id);
                                }
                            } else // state == PlayerState.PlayingAfterPunishment
                                ;
                        } else {
                            if (state == PlayerState.PlayingAfterPunishment)
                                setPlayerState(PlayerState.Playing);
                        }
                    }
                } catch (Exception e) {
                    System.out.printf("Thread player %s run() - after while(!queue.isEmpty())", id); System.out.println();
                    table.wakeUp(); playerThread.interrupted(); break;
                }
            }

        }

        
        if (!human){
                try {
                    aiThread.interrupt();
                    aiThread.join();
                } catch (InterruptedException e) { table.wakeUp();
                    //System.out.printf("AI of player %s was in aiThread.join()", id); System.out.println();
                }
        } 
        
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
        System.out.println("Thread" + Thread.currentThread().getName() + " terminated.");
    }


    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        System.out.println("AI has been created");
        // note: this is a very very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
            System.out.printf("Starting in player %s AI.run()", id); System.out.println();
            while (!terminate && !human) {
                // TODO implement player key press simulator
                //System.out.println("Thread player AI " + Thread.currentThread().getName() + " is playing");
                //System.out.println("table allowing AI player " + id + " to play = " + table.canPlay());
                while (!table.canPlay()) {
                    try {
                        table._wait();
                    } catch (InterruptedException e) {
                        System.out.printf("AI of player %s is waiting for table to play", id); System.out.println();
                        aiThread.interrupted(); table.wakeUp(); break;
                    }
                }

                // For testing AI with human players, here we'll Sleep AI threads a bit for them to not be lightning fast


                while (!terminate && queueOfActions.size() <= dealer.SET_SIZE) {
                    int to_place = rand_generator.nextInt(table.slotToCard.length);
                    if (!table.emptySlots.isEmpty()) {
                        // table.emptySlots = {0,1,2}, to_place = 1, table.emptySlots.indexOf(to_place) != -1 (== 1) -
                        while (!terminate && table.emptySlots.indexOf(to_place) != -1)
                            to_place = rand_generator.nextInt(table.slotToCard.length);
                    }
                    keyPressed(to_place);

                }

            }
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
            System.out.printf("AI thread of player %s has been terminated",id); System.out.println();
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        // TODO implement
        terminate = true;
        table.wakeUp();
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(Integer slot) {

        if (state == PlayerState.Waiting || !table.canPlay() || set_state != SetState.NotComplete || slot == null)
            ;
        else {
            if (state == PlayerState.Playing) {
                if (dealer.tableHaveEmptySlots) {
                    if (table.emptySlots.contains(slot))
                        return;
                    else
                        queueOfActions.offer(slot);
                } else
                    queueOfActions.offer(slot);
            } else if (state == PlayerState.PlayingAfterPunishment) {
                if (dealer.tableHaveEmptySlots) {
                    if (table.emptySlots.contains(slot))
                        return;
                    else {
                        if (mySet.size() == 3) {
                            if (mySet.contains(slot))
                                queueOfActions.offer(slot);
                            else
                                return;
                        } else
                            queueOfActions.offer(slot);
                    }
                } else {
                    if (mySet.size() == 3) {
                        if (mySet.contains(slot))
                            queueOfActions.offer(slot);
                        else
                            return;
                    } else
                        queueOfActions.offer(slot);
                }
            }
            // Alert player that an action was inserted to his queue
            try {
                synchronized (queueOfActions) {
                    _wakeUp();
                }
            } catch (InterruptedException e) { table.wakeUp();}
        }
        //}
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        // TODO implement
        //System.out.printf("player %s in point() method",id); System.out.println();

        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        score += 1;
        env.ui.setScore(id, score);

        if (env.config.pointFreezeMillis != 0) {
            // go into freeze penalty of 1 second && display penalty timer
            try {
                env.ui.setFreeze(id, env.config.pointFreezeMillis);
                Thread.sleep(env.config.pointFreezeMillis);
            } catch (InterruptedException e) {
                System.out.printf("Thread player %s has been shutdown due to closing game window - in point()", id); System.out.println(); table.wakeUp();
            }
            // get out of freeze && stop display of penalty timer
            env.ui.setFreeze(id, 0);
        }

        // change player.set_state back to SetState.NotComplete
        setSetState(SetState.NotComplete);
        // change player.state back to PlayerState.Playing
        setPlayerState(PlayerState.Playing);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        // TODO implement
        //System.out.printf("player %s in penalty() method",id); System.out.println();

        if (env.config.penaltyFreezeMillis != 0) {
            // go into freeze penalty of 3 second && display penalty timer
            long freeze = env.config.penaltyFreezeMillis;
            while (freeze > 0 && !terminate) {
                try {
                    env.ui.setFreeze(id, freeze);
                    Thread.sleep(FREEZE_PENALTY_INTERVAL);
                    freeze -= FREEZE_PENALTY_INTERVAL;
                } catch (InterruptedException e) {
                    System.out.printf("Thread player %s has been shutdown due to closing game window - in penalty()", id); System.out.println();
                    table.wakeUp(); break;
                }
            }
            // get out of freeze && stop display of penalty timer
            env.ui.setFreeze(id, 0);
        }

        // change player.set_state back to SetState.NotComplete
        setSetState(SetState.NotComplete);

    }

    public int score() { return score; }

    // **added methods**

    /**
     * Handling all token wise actions, who can be called from class; Dealer && Player, including removing and placing tokens
     * while updating player's fields
     *
     * @param slot - The slot that the action occurs
     */
    public void handleTokenActions(Integer slot) {

        if (slot == null || table.slotToCard[slot] == null)
            ;
        else {
            if (mySet.indexOf((Integer) slot) != -1) { // There is a token placed on 'slot' - we'll remove it
                table.removeToken(id, slot);
                mySet.remove((Integer) slot);
                slotsToPlace.add(slot);
            } else {
                table.placeToken(id, slot);
                mySet.add(slot);
                slotsToPlace.remove((Integer) slot);
            }
        }
    }

    /**
     * Clearing mySet: set of slots player has placed a token on
     */
    public void clearPlayerSet() {
        mySet = new ArrayList<>();
    }

    /**
     * @param _state - State to assign to Player's state
     */
    public void setPlayerState(PlayerState _state) {
        state = _state;
    }

    /**
     *
     * @param _set_state - SetState to assign to Player's SetState
     */
    public void setSetState(SetState _set_state) {
        set_state = _set_state;
    }

    /**
     * @return Player's SET consist slots player has placed a token on
     */
    public List<Integer> getMySet() {
        return mySet;
    }

    /**
     * @return A clone of player's SET of slots player has placed a token on
     */
    public List<Integer> clone() {
        return (ArrayList<Integer>) ((ArrayList<Integer>) mySet).clone();
    }

    /**
     * @param toRemove - A SET of slots to remove token's from
     */
    public void removeTokensFromSlots(List<Integer> toRemove) {
        for (Integer slot : toRemove) {
            if (slot != null)
                removeTokenFromSlot(slot);
        }

    }

    /**
     * @param slot - A slot to remove tokens from: updating player.mySet && player.slotsToPlace && calling table to remove UI
     */
    public void removeTokenFromSlot(int slot) {
        if (mySet.indexOf((Integer) slot) != -1) {
            mySet.remove((Integer) slot);
            slotsToPlace.add(slot);
            table.removeToken(id, slot);
        }
    }

    /**
     * @return - if player is human
     */
    public boolean isHuman() {
        return human;
    }

    /**
     * @throws InterruptedException
     * Call wait on queue of actions whilst there are no actions inserted to it
     */
    public void _wait() throws InterruptedException {
        queueOfActions.wait();
    }

    /**
     * @throws InterruptedException
     * Notify queue of action that an action has inserted to it
     */
    public void _wakeUp() throws InterruptedException {
        queueOfActions.notify();
    }

    /**
     *
     * @param state - Player need to assign his state to it:
     *              PlayerState.Playing if SET of player is Legal
     *              PlayerState.PlayingAfterPunishment if SET of player is Illegal
     * @throws InterruptedException
     * Will be called only from dealer.checkSet() after dealer has finished checking player's SET
     */
    public void _notify(PlayerState state) throws InterruptedException {
        synchronized (obj) {
            setPlayerState(state);
            obj.notify();
        }
    }

    /**
     * @throws InterruptedException
     * Will be called only at the end of dealer.placeCardsOnTable(), in order to notify all player's that they can return playing
     */
    public void _notifyAll() throws InterruptedException {
        synchronized (obj) {
            obj.notifyAll();
        }
    }

    public Object getObj(){
        return obj;
    }
}
