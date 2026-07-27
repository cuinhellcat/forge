package forge.ai;

import forge.card.CardStateName;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.zone.PlayerZoneBattlefield;
import forge.game.zone.ZoneType;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

/**
 * A melded permanent is two cards: the front half on the battlefield in its Meld state, and
 * the back half held in the battlefield zone's melded collection rather than its card list.
 * Snapshots have to carry that distinction in both directions.
 */
public class SnapshotMeldTest extends AITest {

    private Card primary;
    private Card secondary;

    /** Melds the two cards the way MeldEffect does. */
    private void meld(Game game, Player p, Card bruna, Card gisela) {
        primary = bruna.hasState(CardStateName.Meld) ? bruna : gisela;
        secondary = primary == bruna ? gisela : bruna;
        AssertJUnit.assertTrue("one half carries the meld state", primary.hasState(CardStateName.Meld));
        primary.changeToState(CardStateName.Meld);
        primary.setBackSide(true);
        primary.setMeldedWith(secondary);
        ((PlayerZoneBattlefield) p.getZone(ZoneType.Battlefield)).addToMelded(secondary);
    }

    private Game meldableGame() {
        Game game = initAndCreateGame();
        game.EXPERIMENTAL_RESTORE_SNAPSHOT = true;
        game.getPhaseHandler().setPriority(game.getPlayers().get(0));
        return game;
    }

    @Test
    public void restoringPastAMeldUnmeldsBothHalves() {
        Game game = meldableGame();
        Player p = game.getPlayers().get(0);
        PlayerZoneBattlefield battlefield = (PlayerZoneBattlefield) p.getZone(ZoneType.Battlefield);

        Card bruna = addCard("Bruna, the Fading Light", p);
        Card gisela = addCard("Gisela, the Broken Blade", p);

        game.stashGameState(); // before the meld

        meld(game, p, bruna, gisela);
        AssertJUnit.assertTrue(game.restoreGameState());

        AssertJUnit.assertNotNull("both halves survive", game.findById(bruna.getId()));
        AssertJUnit.assertNotNull("both halves survive", game.findById(gisela.getId()));
        AssertJUnit.assertEquals("both are loose on the battlefield again", 2,
                battlefield.getCards().size());
        AssertJUnit.assertFalse("the back half is no longer held as melded",
                battlefield.getMeldedCards().contains(game.findById(secondary.getId())));
        AssertJUnit.assertNull("and the front half no longer points at it",
                game.findById(primary.getId()).getMeldedWith());
    }

    @Test
    public void restoringToAMeldKeepsItWhole() {
        Game game = meldableGame();
        Player p = game.getPlayers().get(0);
        PlayerZoneBattlefield battlefield = (PlayerZoneBattlefield) p.getZone(ZoneType.Battlefield);

        Card bruna = addCard("Bruna, the Fading Light", p);
        Card gisela = addCard("Gisela, the Broken Blade", p);
        meld(game, p, bruna, gisela);

        game.stashGameState(); // with the meld in play

        // Something to roll back. Deliberately not a new card: creating one lands in the
        // separate null dereference that #11416 is about.
        primary.setTapped(true);
        AssertJUnit.assertTrue(game.restoreGameState());
        AssertJUnit.assertFalse("the roll back happened", game.findById(primary.getId()).isTapped());

        Card restoredPrimary = game.findById(primary.getId());
        Card restoredSecondary = game.findById(secondary.getId());
        AssertJUnit.assertNotNull(restoredPrimary);
        AssertJUnit.assertNotNull(restoredSecondary);
        AssertJUnit.assertEquals("the front half is still melded", CardStateName.Meld,
                restoredPrimary.getCurrentStateName());
        AssertJUnit.assertEquals("and still points at the back half",
                restoredSecondary, restoredPrimary.getMeldedWith());
        AssertJUnit.assertTrue("which is still held as melded",
                battlefield.getMeldedCards().contains(restoredSecondary));
        AssertJUnit.assertEquals("one permanent, not two", 1, battlefield.getCards().size());
    }
}
