package bguspl.set.ex;

import bguspl.set.Env;

import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This class contains the data that is visible to the player.
 *
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 */
public class Table {

    /**
     * The game environment object.
     */
    public final Env env; // public for tests

    /**
     * Mapping between a slot and the card placed in it (null if none).
     */
    protected final Integer[] slotToCard; // card per slot (if any)

    /**
     * Mapping between a card and the slot it is in (null if none).
     */
    protected final Integer[] cardToSlot; // slot per card (if any)

    // added fields
    /**
     * Represents the current empty slots on table (i.e. slots without a card)
     */
    protected List<Integer> emptySlots; // after CTR: {0,1,2,3,4,5,6,7,8,9,10,11} -> after first placement of all cards on table: {} -> after a player has found a legal set and have them removed from table: {8,2,0}

    /**
     * Slot lock who permits only one player at a time to place or remove a token while keeping fairness of the game
     */
    protected ReentrantLock fairSlotsLock;

    /**
     * Indicator whether player can play on table;
     * true if player is allowed to place\remove token freely
     * false if dealer is placing\removing cards from table thus player isn't allowed to play
     */
    protected volatile boolean canPlay = false;


    /**
     * Constructor for testing.
     *
     * @param env        - the game environment objects.
     * @param slotToCard - mapping between a slot and the card placed in it (null if none).
     * @param cardToSlot - mapping between a card and the slot it is in (null if none).
     */
    public Table(Env env, Integer[] slotToCard, Integer[] cardToSlot) {

        this.env = env;
        this.slotToCard = slotToCard; // slotToCard.size()==12
        this.cardToSlot = cardToSlot; // cardToSlot.size()==81

        // added to CTR

        // #1 Initialize added fields
        emptySlots = new ArrayList<>();
        fairSlotsLock = new ReentrantLock(true);

        // #2 Initialize all values to null
        // slotToCard[i] == null means that in the ith slot there is no card placed
        for (int i = 0; i < slotToCard.length; ++i) {
            slotToCard[i] = null;
            emptySlots.add(i);
        }

        // cardToSlot[j] == null means that the jth card isn't placed in any slot on grid
        for (int j = 0; j < cardToSlot.length; ++j)
            cardToSlot[j] = null;

    }

    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
    public Table(Env env) {

        this(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);
    }

    /**
     * This method prints all possible legal sets of cards that are currently on the table.
     */
    public void hints() {
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
            StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted().collect(Collectors.toList());
            int[][] features = env.util.cardsToFeatures(set);
            System.out.println(sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
        });
    }

    public boolean hasClue() {
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        return env.util.findSets(deck,Integer.MAX_VALUE).size() > 0;
    }

    public Integer[] getHint() {
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        Integer[] ans = new Integer[3];
        List<int[]> hints_list = env.util.findSets(deck,Integer.MAX_VALUE);
        Random rand = new Random();
        int[] hint = hints_list.get(rand.nextInt(hints_list.size())); // pick a random hint for a SET that currently placed on table
        for (int h = 0; h < hint.length; ++h)
            ans[h] = cardToSlot[hint[h]];

        Arrays.sort(ans);
        return ans;
    }

    /**
     * Count the number of cards currently on the table.
     *
     * @return - the number of cards on the table.
     */
    public int countCards() {
        int cards = 0;
        for (Integer card : slotToCard)
            if (card != null)
                ++cards;
        return cards;
    }

    /**
     * Places a card on the table in a grid slot.
     * @param card - the card id to place in the slot.
     * @param slot - the slot in which the card should be placed.
     *
     * @post - the card placed is on the table, in the assigned slot.
     */
    public void placeCard(int card, int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}

        cardToSlot[card] = slot;
        slotToCard[slot] = card;
        env.ui.placeCard(card, slot);

        // Dealer himself in placeCardsOnTable() removes the emptySlot from table.emptySlots

        // TODO implement
    }

    /**
     * Removes a card from a grid slot on the table.
     * @param slot - the slot from which to remove the card.
     */
    public void removeCard(Integer slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}

        // TODO implement

        // update mapping arrays
        if (slotToCard[slot] != null) {
            int card =  slotToCard[slot];
            cardToSlot[card] = null;
            slotToCard[slot] = null;
            // update UI
            env.ui.removeCard(slot);
        }

        // update empty Slots List
        if (!emptySlots.contains(slot))
            emptySlots.add(slot);
    }

    /**
     * Places a player token on a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     */
    public void placeToken(int player, int slot) {
        // TODO implement
        env.ui.placeToken(player,slot);
    }

    /**
     * Removes a token of a player from a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return       - true iff a token was successfully removed.
     */
    public boolean removeToken(int player, int slot) {
        // TODO implement
        try {
            env.ui.removeToken(player, slot);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // added methods

    public void removeTokens(int slot) {
        env.ui.removeTokens(slot);
    }

    /**
     * @return If player's can return playing and placing tokens
     */
    public boolean canPlay() {
        return canPlay;
    }

    /**
     * Dealer is informing that playing can be resumed, it means dealer has finished placing cards on table
     */
    public void gameOn() {
        canPlay = true;
    }

    /**
     * Dealer is informing that paying need to be paused, it means dealer has started removing cards from table
     */
    public void gameOff() {
        canPlay = false;
    }

    /**
     * Notify all player's waiting on table to wake-up, it means they can resume playing
     */
    public synchronized void wakeUp() {
        notifyAll();
    }

    /**
     * @throws InterruptedException
     * Inform player's to stop playing and wait to table's command in order to resume playing
     */
    public synchronized void _wait() throws InterruptedException {
        wait();
    }

}
