package bguspl.set.ex;

import bguspl.set.Config;
import bguspl.set.Env;
import bguspl.set.UserInterface;
import bguspl.set.Util;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.*;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class DealerTest {

    Dealer dealer;
    Table table;
    private Integer[] slotToCard;
    private Integer[] cardToSlot;

    Player[] players;

    @BeforeEach
    void setUp() {

        Properties properties = new Properties();
        properties.put("Rows", "2");
        properties.put("Columns", "2");
        properties.put("FeatureSize", "3");
        properties.put("FeatureCount", "4");
        properties.put("TableDelaySeconds", "0");
        properties.put("PlayerKeys1", "81,87,69,82");
        properties.put("PlayerKeys2", "85,73,79,80");
        MockLogger logger = new MockLogger();
        Config config = new Config(logger, properties);
        slotToCard = new Integer[config.tableSize];
        cardToSlot = new Integer[config.deckSize];

        Env env = new Env(logger, config, new MockUserInterface(), new MockUtil());
        table = new Table(env, slotToCard, cardToSlot);

        players = new Player[config.players];
        for(int i=0 ; i < players.length ; ++i){
            players[i] = new Player(env, dealer, table, i, i < config.humanPlayers);
        }

        dealer = new Dealer(env, table, players);
    }

    @Test
    void addPlayerToQueue(){
        int expectedQueueSize = dealer.playersToCheck.size() + 1;
        
        // call the method we are testing
        dealer.addPlayerToQueue(0);

        // verify that the Queue size increased by 1
        assertEquals(expectedQueueSize, dealer.playersToCheck.size());

        // verify that the player was added to the queue
        assertTrue(dealer.playersToCheck.contains(0));
    }

    @Test
    void resestGame(){
        PlayerState expectedPlayerState = PlayerState.Playing;
        SetState expectedSetState = SetState.NotComplete;

        // call the method we are testing
        dealer.resetGame();

        // verify update of player and set states
        //for(Player player: players) {
        for (int i = 0; i < players.length; ++i) {
            assertEquals(expectedPlayerState, players[i].state);
            assertEquals(expectedSetState, players[i].set_state);
            assertTrue(players[i].mySet.size() == 0);
        }

        assertTrue(dealer.playersToCheck.size() == 0);
    }
    
    // @Test
    // void placeCardsOnTable_BigDeck(){
    //     int preSlotSize = table.emptySlots.size();
    //     int preDeckSize = dealer.deck.size();
    //     boolean preCanPlay = table.canPlay; // false at initiation

    //     // call the method we are testing
    //     dealer.placeCardsOnTable();

    //     // check that the proper number of cards we placed on slots
    //     assertEquals(preDeckSize-preSlotSize, dealer.deck.size());
    //     assertEquals(0, table.emptySlots.size());

    //     // verify that after calling dealer.placeCardsOnTable(), table.canPlay is updated
    //     assertTrue(preCanPlay != table.canPlay);

    //     // already checked UI at TableTest
    // }

    // @Test

    // void placeCardsOnTable_SmallDeck(){
    //     List<Integer> smallDeck = new ArrayList<>(2);
    //     smallDeck.add(dealer.deck.remove(0));
    //     smallDeck.add(dealer.deck.remove(0));
    //     dealer.deck = smallDeck;
    //     int preSlotSize = table.emptySlots.size();
    //     System.out.println("preSlotSize = " + preSlotSize);
    //     int preDeckSize = dealer.deck.size();
    //     System.out.println("preDeckSize = " + preDeckSize);

    //     // call the method we are testing
    //     dealer.placeCardsOnTable();

    //     // check that the proper number of cards we placed on slots
    //     assertEquals(0, dealer.deck.size());
    //     assertEquals(preSlotSize-preDeckSize, table.emptySlots.size());
    // }

    static class MockUserInterface implements UserInterface {
        @Override
        public void dispose() {}
        @Override
        public void placeCard(int card, int slot) {}
        @Override
        public void removeCard(int slot) {}
        @Override
        public void setCountdown(long millies, boolean warn) {}
        @Override
        public void setElapsed(long millies) {}
        @Override
        public void setScore(int player, int score) {}
        @Override
        public void setFreeze(int player, long millies) {}
        @Override
        public void placeToken(int player, int slot) {}
        @Override
        public void removeTokens() {}
        @Override
        public void removeTokens(int slot) {}
        @Override
        public void removeToken(int player, int slot) {}
        @Override
        public void announceWinner(int[] players) {}
    };

    static class MockUtil implements Util {
        @Override
        public int[] cardToFeatures(int card) {
            return new int[0];
        }

        @Override
        public int[][] cardsToFeatures(int[] cards) {
            return new int[0][];
        }

        @Override
        public boolean testSet(int[] cards) {
            return false;
        }

        @Override
        public List<int[]> findSets(List<Integer> deck, int count) {
            return null;
        }

        @Override
        public void spin() {}
    }

    static class MockLogger extends Logger {
        protected MockLogger() {
            super("", null);
        }
    }
}
