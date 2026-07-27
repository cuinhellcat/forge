package forge.ai;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

/**
 * Reproduces #9297: taking a snapshot crashes with
 * "IndexOutOfBoundsException: Index: 9, Size: 7" when a card whose controller is not its
 * owner sits in an ordered hidden zone.
 *
 * Such a card belongs to its owner's graveyard, but the snapshot files it under the
 * controller, so it is inserted into the wrong player's graveyard at the index it had in
 * the owner's — past the end of the shorter list.
 */
public class SnapshotStolenCardTest extends AITest {

    @Test
    public void snapshotWithAStolenCardInAGraveyard() {
        Game game = initAndCreateGame();
        game.EXPERIMENTAL_RESTORE_SNAPSHOT = true;
        Player owner = game.getPlayers().get(0);
        Player thief = game.getPlayers().get(1);
        game.getPhaseHandler().setPriority(owner);

        // The thief's graveyard is shorter than the position the stolen card has in the
        // owner's — 7 and 9 in the report.
        for (int i = 0; i < 7; i++) {
            addCardToZone("Mountain", thief, ZoneType.Graveyard);
        }
        for (int i = 0; i < 9; i++) {
            addCardToZone("Forest", owner, ZoneType.Graveyard);
        }
        Card stolen = addCardToZone("Island", owner, ZoneType.Graveyard);
        stolen.setController(thief, game.getNextTimestamp());

        AssertJUnit.assertEquals(thief, stolen.getController());
        AssertJUnit.assertEquals(owner, stolen.getOwner());
        AssertJUnit.assertEquals(9, owner.getZone(ZoneType.Graveyard).getCards().indexOf(stolen));

        game.stashGameState(); // threw IndexOutOfBoundsException

        // The snapshot has to keep the card in the graveyard it is actually in.
        AssertJUnit.assertTrue(game.restoreGameState());
        Card after = game.findById(stolen.getId());
        AssertJUnit.assertNotNull("stolen card survived the snapshot", after);
        AssertJUnit.assertEquals(ZoneType.Graveyard, after.getZone().getZoneType());
        AssertJUnit.assertEquals("still in its owner's graveyard", owner, after.getZone().getPlayer());
    }
}
