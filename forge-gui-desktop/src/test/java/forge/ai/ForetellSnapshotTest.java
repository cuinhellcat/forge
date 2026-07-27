package forge.ai;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

/**
 * Reproduces #7844: a foretold card that is cast from exile and then cancelled comes back
 * face up and can no longer be cast with Foretell.
 *
 * The steps below are what the cancel does internally: a snapshot is taken while the card
 * sits face down in exile, casting turns it face up and moves it to the stack, and the
 * rollback restores the snapshot.
 */
public class ForetellSnapshotTest extends AITest {

    @Test
    public void foretoldCardIsStillForetoldAfterRollback() {
        Game game = initAndCreateGame();
        game.EXPERIMENTAL_RESTORE_SNAPSHOT = true;
        Player p = game.getPlayers().get(0);
        game.getPhaseHandler().setPriority(p);

        Card card = addCardToZone("Doomskar Oracle", p, ZoneType.Exile);
        card.setForetold(true);
        card.turnFaceDown(true);
        AssertJUnit.assertTrue(card.isForetold());
        AssertJUnit.assertTrue(card.isFaceDown());

        game.stashGameState();

        // Cast it: face up, onto the stack.
        card.turnFaceUp(false, null);
        game.getAction().moveTo(ZoneType.Stack, card, null, null);

        // Cancel: roll back to the snapshot.
        AssertJUnit.assertTrue(game.restoreGameState());

        Card after = game.findById(card.getId());
        AssertJUnit.assertNotNull("card should still be in the game", after);
        AssertJUnit.assertEquals("card should be back in exile", ZoneType.Exile, after.getZone().getZoneType());
        AssertJUnit.assertTrue("card should be face down again", after.isFaceDown());
        AssertJUnit.assertTrue("card should still be foretold", after.isForetold());
    }
}
