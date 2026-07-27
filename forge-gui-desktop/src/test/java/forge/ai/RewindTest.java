package forge.ai;

import forge.game.Game;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

/**
 * Tests for the turn based rewind (Game#stashTurnRewindPoint, Game#rewindToActionOf),
 * which restarts one of the player's own turns from its beginning and throws away
 * everything that happened since.
 *
 * A point is recorded once per turn, in the turn player's first main phase with an empty
 * stack, because that is the only moment the save format describes without gaps.
 */
public class RewindTest extends AITest {

    /** Puts the game at the start of the given player's turn, ready for a rewind point. */
    private void startTurn(Game game, Player p, int turn) {
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, p, turn);
        game.getPhaseHandler().setPriority(p);
    }

    /**
     * Restoring a state runs on the game thread, which in a real match is the thread the
     * rewind is triggered from. Tests have no such thread, so borrow one — GameAction
     * recognises it by its name.
     */
    private boolean rewind(final Game game, final Player p, final int steps) {
        final boolean[] result = new boolean[1];
        final Thread t = new Thread(() -> result[0] = game.rewindToActionOf(p, steps), "Game-test");
        t.start();
        try {
            t.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return result[0];
    }

    @Test
    public void rewindRestartsTheCurrentTurn() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);

        startTurn(game, p, 3);
        game.stashTurnRewindPoint(p);

        addCard("Mountain", p);
        p.setLife(15, null);

        AssertJUnit.assertEquals(1, game.getAvailableRewindSteps(p));
        AssertJUnit.assertTrue(rewind(game, p, 1));

        Player after = game.getPlayers().get(0);
        AssertJUnit.assertEquals(0, after.getCardsIn(ZoneType.Battlefield).size());
        AssertJUnit.assertEquals(20, after.getLife());
    }

    @Test
    public void rewindGoesBackSeveralOwnTurns() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);

        startTurn(game, p, 1);
        game.stashTurnRewindPoint(p);
        addCard("Mountain", p);

        startTurn(game, p, 3);
        game.stashTurnRewindPoint(p);
        addCard("Forest", p);

        startTurn(game, p, 5);
        game.stashTurnRewindPoint(p);
        addCard("Island", p);

        AssertJUnit.assertEquals(3, game.getAvailableRewindSteps(p));

        // Three steps back is the oldest point: none of the lands had been played yet.
        AssertJUnit.assertTrue(rewind(game, p, 3));
        AssertJUnit.assertEquals(1, game.getPhaseHandler().getTurn());
        AssertJUnit.assertEquals(0, game.getPlayers().get(0).getCardsIn(ZoneType.Battlefield).size());
    }

    @Test
    public void rewindUndoesWhatHappenedInBetween() {
        Game game = initAndCreateGame();
        Player me = game.getPlayers().get(0);
        Player other = game.getPlayers().get(1);

        startTurn(game, me, 3);
        game.stashTurnRewindPoint(me);
        addCard("Mountain", me);

        // The opponent's turn follows and they get something onto the battlefield too.
        startTurn(game, other, 4);
        addCard("Swamp", other);

        startTurn(game, me, 5);

        // Back to the start of my turn 3: my Mountain and their Swamp are both gone.
        AssertJUnit.assertTrue(rewind(game, me, 1));
        AssertJUnit.assertEquals(0, game.getPlayers().get(0).getCardsIn(ZoneType.Battlefield).size());
        AssertJUnit.assertEquals(0, game.getPlayers().get(1).getCardsIn(ZoneType.Battlefield).size());
    }

    @Test
    public void rewindRestoresTurnPhaseAndPriority() {
        Game game = initAndCreateGame();
        Player me = game.getPlayers().get(0);
        Player other = game.getPlayers().get(1);
        PhaseHandler ph = game.getPhaseHandler();

        startTurn(game, me, 7);
        game.stashTurnRewindPoint(me);

        ph.devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS, other, 8);
        ph.setPriority(other);

        AssertJUnit.assertTrue(rewind(game, me, 1));
        AssertJUnit.assertEquals(7, ph.getTurn());
        AssertJUnit.assertEquals(PhaseType.MAIN1, ph.getPhase());
        AssertJUnit.assertEquals(me, ph.getPriorityPlayer());
        AssertJUnit.assertEquals(me, ph.getPlayerTurn());
    }

    @Test
    public void onlyOnePointPerTurnAndOnlyAtTheRightMoment() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        Player other = game.getPlayers().get(1);

        startTurn(game, p, 3);
        game.stashTurnRewindPoint(p);
        game.stashTurnRewindPoint(p);   // same turn again: ignored
        AssertJUnit.assertEquals(1, game.getAvailableRewindSteps(p));

        // Not the turn player.
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, other, 4);
        game.stashTurnRewindPoint(p);
        AssertJUnit.assertEquals(1, game.getAvailableRewindSteps(p));

        // Own turn, but not the first main phase.
        game.getPhaseHandler().devModeSet(PhaseType.UPKEEP, p, 5);
        game.stashTurnRewindPoint(p);
        AssertJUnit.assertEquals(1, game.getAvailableRewindSteps(p));
    }

    @Test
    public void rewindIsLimitedToTheConfiguredDepth() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);

        for (int turn = 1; turn <= 10; turn++) {
            startTurn(game, p, turn);
            game.stashTurnRewindPoint(p);
        }

        AssertJUnit.assertEquals(game.REWIND_STEPS, game.getAvailableRewindSteps(p));
        AssertJUnit.assertFalse(rewind(game, p, game.REWIND_STEPS + 1));

        // The oldest points were dropped, so the deepest step is not turn 1 any more.
        AssertJUnit.assertTrue(rewind(game, p, game.REWIND_STEPS));
        AssertJUnit.assertEquals(11 - game.REWIND_STEPS, game.getPhaseHandler().getTurn());
    }

    @Test
    public void rewindIsOffWhenTheDepthIsZero() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        game.REWIND_STEPS = 0;

        startTurn(game, p, 3);
        game.stashTurnRewindPoint(p);

        AssertJUnit.assertEquals(0, game.getAvailableRewindSteps(p));
        AssertJUnit.assertFalse(rewind(game, p, 1));
    }
}
