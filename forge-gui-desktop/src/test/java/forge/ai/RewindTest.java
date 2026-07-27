package forge.ai;

import forge.game.Game;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

/**
 * Tests for the multi step rewind (Game#rewindToActionOf), which lets the player running
 * the game take back their last few actions along with everything that happened after.
 *
 * A rewind point is stashed every time a player is about to act, so the newest one is
 * always "right now" and is skipped when counting steps back.
 */
public class RewindTest extends AITest {

    private Game gameWithRewind() {
        Game game = initAndCreateGame();
        game.EXPERIMENTAL_RESTORE_SNAPSHOT = true;
        return game;
    }

    @Test
    public void rewindTakesBackTheLastAction() {
        Game game = gameWithRewind();
        Player p = game.getPlayers().get(0);
        game.getPhaseHandler().setPriority(p);

        game.stashGameState();                  // before the action
        addCard("Mountain", p);
        p.setLife(15, null);
        game.stashGameState();                  // "now"

        AssertJUnit.assertEquals(1, game.getAvailableRewindSteps(p));
        AssertJUnit.assertTrue(game.rewindToActionOf(p, 1));

        AssertJUnit.assertEquals(0, game.getPlayers().get(0).getCardsIn(ZoneType.Battlefield).size());
        AssertJUnit.assertEquals(20, game.getPlayers().get(0).getLife());
    }

    @Test
    public void rewindGoesBackSeveralOwnActions() {
        Game game = gameWithRewind();
        Player p = game.getPlayers().get(0);
        game.getPhaseHandler().setPriority(p);

        game.stashGameState();
        addCard("Mountain", p);
        game.stashGameState();
        addCard("Forest", p);
        game.stashGameState();
        addCard("Island", p);
        game.stashGameState();                  // "now": three lands on the battlefield

        AssertJUnit.assertEquals(3, game.getAvailableRewindSteps(p));

        // Two steps back: the Island and the Forest are gone, the Mountain stays.
        AssertJUnit.assertTrue(game.rewindToActionOf(p, 2));
        AssertJUnit.assertEquals(1, game.getPlayers().get(0).getCardsIn(ZoneType.Battlefield).size());
        AssertJUnit.assertEquals(1, countCardsWithName(game, "Mountain"));
    }

    @Test
    public void rewindSkipsOtherPlayersPoints() {
        Game game = gameWithRewind();
        Player me = game.getPlayers().get(0);
        Player other = game.getPlayers().get(1);
        PhaseHandler ph = game.getPhaseHandler();

        ph.setPriority(me);
        game.stashGameState();                  // my point, before my action
        addCard("Mountain", me);

        ph.setPriority(other);
        game.stashGameState();                  // the opponent acts in between
        addCard("Swamp", other);

        ph.setPriority(me);
        game.stashGameState();                  // "now"

        // One step back for me undoes my Mountain *and* the opponent's Swamp with it.
        AssertJUnit.assertTrue(game.rewindToActionOf(me, 1));
        AssertJUnit.assertEquals(0, game.getPlayers().get(0).getCardsIn(ZoneType.Battlefield).size());
        AssertJUnit.assertEquals(0, game.getPlayers().get(1).getCardsIn(ZoneType.Battlefield).size());
    }

    @Test
    public void rewindRestoresTurnAndPriority() {
        Game game = gameWithRewind();
        Player me = game.getPlayers().get(0);
        Player other = game.getPlayers().get(1);
        PhaseHandler ph = game.getPhaseHandler();

        ph.setPriority(me);
        game.stashGameState();
        int turnBefore = ph.getTurn();

        // The game moves on: other player's turn, later phase.
        ph.devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS, other, turnBefore + 1);
        ph.setPriority(other);
        game.stashGameState();

        ph.setPriority(me);
        game.stashGameState();                  // "now"

        AssertJUnit.assertTrue(game.rewindToActionOf(me, 1));
        AssertJUnit.assertEquals(turnBefore, ph.getTurn());
        AssertJUnit.assertEquals(PhaseType.MAIN1, ph.getPhase());
        AssertJUnit.assertEquals(me, ph.getPriorityPlayer());
    }

    @Test
    public void rewindIsLimitedAndDisabledWithoutSnapshots() {
        Game game = gameWithRewind();
        Player p = game.getPlayers().get(0);
        game.getPhaseHandler().setPriority(p);

        for (int i = 0; i < 10; i++) {
            game.stashGameState();
            addCard("Mountain", p);
        }
        game.stashGameState();

        // Never more than the configured number of steps, and asking for more fails.
        AssertJUnit.assertEquals(game.REWIND_STEPS, game.getAvailableRewindSteps(p));
        AssertJUnit.assertFalse(game.rewindToActionOf(p, game.REWIND_STEPS + 1));

        // Nothing is stored at all while the feature is off.
        Game plain = initAndCreateGame();
        Player q = plain.getPlayers().get(0);
        plain.getPhaseHandler().setPriority(q);
        plain.stashGameState();
        plain.stashGameState();
        AssertJUnit.assertEquals(0, plain.getAvailableRewindSteps(q));
        AssertJUnit.assertFalse(plain.rewindToActionOf(q, 1));
    }
}
