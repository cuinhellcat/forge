package forge.ai;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

/**
 * The save format does not record a player's maximum hand size, so a rewind can only get
 * it right if it is recomputed from what is on the battlefield. These tests check that it
 * is, in both directions.
 */
public class RewindHandSizeTest extends AITest {

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

    private void startTurn(Game game, Player p, int turn) {
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, p, turn);
        game.getPhaseHandler().setPriority(p);
    }

    @Test
    public void rewindTakesBackAnUnlimitedHandSize() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        startTurn(game, p, 3);

        game.stashTurnRewindPoint(p);
        AssertJUnit.assertFalse("no tower yet", p.isUnlimitedHandSize());

        addCard("Reliquary Tower", p);
        game.getAction().checkStateEffects(true);
        AssertJUnit.assertTrue("tower grants it", p.isUnlimitedHandSize());

        AssertJUnit.assertTrue(rewind(game, p, 1));

        Player after = game.getPlayers().get(0);
        AssertJUnit.assertFalse("gone again with the tower", after.isUnlimitedHandSize());
        AssertJUnit.assertEquals(7, after.getMaxHandSize());
    }

    @Test
    public void rewindGivesAnUnlimitedHandSizeBack() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        startTurn(game, p, 3);

        Card tower = addCard("Reliquary Tower", p);
        game.getAction().checkStateEffects(true);
        AssertJUnit.assertTrue(p.isUnlimitedHandSize());

        game.stashTurnRewindPoint(p);

        game.getAction().destroy(tower, null, true, null);
        game.getAction().checkStateEffects(true);
        AssertJUnit.assertFalse("tower destroyed", p.isUnlimitedHandSize());

        AssertJUnit.assertTrue(rewind(game, p, 1));

        Player after = game.getPlayers().get(0);
        AssertJUnit.assertTrue("tower is back, and so is the effect", after.isUnlimitedHandSize());
    }
}
