package bguspl.set.ex;

import bguspl.set.Config;
import bguspl.set.Env;
import bguspl.set.UserInterface;
import bguspl.set.Util;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlayerTest {

    Player player;
    @Mock
    Util util;
    @Mock
    private UserInterface ui;
    @Mock
    private Table table;
    @Mock
    private Dealer dealer;
    @Mock
    private Logger logger;

    void assertInvariants() {
        assertTrue(player.id >= 0);
        assertTrue(player.score() >= 0);
    }

    @BeforeEach
    void setUp() {
        // purposely do not find the configuration files (use defaults here).
        Env env = new Env(logger, new Config(logger, (String) null), ui, util);
        player = new Player(env, dealer, table, 0, false);
        assertInvariants();
    }

    @AfterEach
    void tearDown() {
        assertInvariants();
    }

    void partiallyFillMySet(){
        player.mySet.add(0);
        table.placeToken(player.id, 0);
        player.mySet.add(1);
        table.placeToken(player.id, 1);
    }
    @Test
    void point() {

        // force table.countCards to return 3
        when(table.countCards()).thenReturn(3); // this part is just for demonstration

        // calculate the expected score for later
        int expectedScore = player.score() + 1;

        // call the method we are testing
        player.point();

        // check that the score was increased correctly
        assertEquals(expectedScore, player.score());

        // check that ui.setScore was called with the player's id and the correct score
        verify(ui).setScore(eq(player.id), eq(expectedScore));
    }

    @Test
    void penalty() {

        // calculate the expected score for later
        int expectedScore = player.score();

        // call the method we are testing
        player.penalty();

        // check that the score was increased correctly
        assertEquals(expectedScore, player.score());

        // check that ui.setFreeze was called with the player's id and the correct time
        // long expectedPenaltyFreezeMillis = 3000;

        // while(expectedPenaltyFreezeMillis >= 0){
        //     verify(ui).setFreeze(eq(player.id), eq(expectedPenaltyFreezeMillis));
        //     expectedPenaltyFreezeMillis -= 1000;
        // }

        // verify that SetState was properly updated
        assertTrue(player.set_state == SetState.NotComplete);
    }

    @Test
    void clearPlayerSet(){
        player.clearPlayerSet();
        assertTrue(player.mySet.size() == 0);
    }
    
    // @Test
    // void handleTokenAction(){
    //     // FIRST, check when a token need to be removed from mySet and from the table
    //     partiallyFillMySet();
    //     Integer slot = 0;
    //     List<Integer> expectedList = new ArrayList<>();
    //     expectedList.add(1);

    //     // call the method we are testing
    //     player.handleTokenActions(slot);

    //     // check that ui.removeToken was called with the player's id and the correct slot
    //     verify(table).removeToken(eq(player.id), eq(slot));

    //     // check that the slot removed successfully from mySet
    //     assertEquals(expectedList, player.mySet);

    //     // check that slot was added to slotToPlace
    //     assertTrue(player.slotsToPlace.contains(slot));

    //     // SECOND, check when a token need to be inserted to mySet and placed on table
    //     Integer anotherSlot = 2;

    //     // call the method we are testing
    //     player.handleTokenActions(anotherSlot);

    //     // check that ui.removeToken was called with the player's id and the correct slot
    //     verify(table).placeToken(eq(player.id), eq(anotherSlot));

    //     // check that the slot added successfully to mySet
    //     assertTrue(player.mySet.contains(anotherSlot));

    //     // check that slot was removed from slotToPlace
    //     assertFalse(player.slotsToPlace.contains(anotherSlot));
    // }
}