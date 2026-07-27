package forge.ai;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

/**
 * Praetor's Counsel grants "you have no maximum hand size" for the rest of the game, and
 * then goes to the graveyard. Nothing on the battlefield carries the effect afterwards —
 * it lives on as an Effect card in the command zone. Does a rewind get that right?
 */
public class RewindLastingEffectTest extends AITest {

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

    private void castPraetorsCounsel(Game game, Player p) {
        Card counsel = addCardToZone("Praetor's Counsel", p, ZoneType.Hand);
        SpellAbility sa = counsel.getFirstSpellAbility();
        sa.setActivatingPlayer(p);
        game.getStack().add(sa);
        playUntilStackClear(game);
        game.getAction().checkStateEffects(true);
    }

    @Test
    public void rewindTakesBackARestOfGameEffect() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, p, 3);
        game.getPhaseHandler().setPriority(p);

        game.stashTurnRewindPoint(p);
        AssertJUnit.assertFalse("not yet cast", p.isUnlimitedHandSize());

        castPraetorsCounsel(game, p);
        AssertJUnit.assertTrue("the spell granted it", p.isUnlimitedHandSize());
        AssertJUnit.assertEquals("and nothing on the battlefield carries it", 0,
                p.getCardsIn(ZoneType.Battlefield).size());

        AssertJUnit.assertTrue(rewind(game, p, 1));

        Player after = game.getPlayers().get(0);
        AssertJUnit.assertFalse("gone again after the rewind", after.isUnlimitedHandSize());
        AssertJUnit.assertEquals(7, after.getMaxHandSize());
    }

    @Test
    public void rewindKeepsARestOfGameEffectItWasTakenAfter() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, p, 3);
        game.getPhaseHandler().setPriority(p);

        castPraetorsCounsel(game, p);
        AssertJUnit.assertTrue(p.isUnlimitedHandSize());

        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, p, 5);
        game.getPhaseHandler().setPriority(p);
        game.stashTurnRewindPoint(p);

        addCard("Mountain", p);
        AssertJUnit.assertTrue(rewind(game, p, 1));

        Player after = game.getPlayers().get(0);
        AssertJUnit.assertEquals("the Mountain is gone", 0, after.getCardsIn(ZoneType.Battlefield).size());
        AssertJUnit.assertTrue("but the rest-of-game effect is still there",
                after.isUnlimitedHandSize());
    }
}
