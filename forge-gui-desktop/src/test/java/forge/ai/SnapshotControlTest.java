package forge.ai;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

/**
 * Restoring a snapshot has to give a stolen creature back. Reported from play: casting
 * Claim the Firstborn and then rewinding returned the spell to hand but left the creature
 * under the thief's control.
 */
public class SnapshotControlTest extends AITest {

    @Test
    public void restoringUndoesAStolenCreature() {
        Game game = initAndCreateGame();
        game.EXPERIMENTAL_RESTORE_SNAPSHOT = true;
        Player owner = game.getPlayers().get(0);
        Player thief = game.getPlayers().get(1);
        game.getPhaseHandler().setPriority(thief);

        Card creature = addCard("Grizzly Bears", owner);
        AssertJUnit.assertEquals(owner, creature.getController());

        game.stashGameState();

        // Claim the Firstborn: gain control of the creature.
        creature.setController(thief, game.getNextTimestamp());
        game.getAction().controllerChangeZoneCorrection(creature);
        AssertJUnit.assertEquals(thief, creature.getController());
        AssertJUnit.assertEquals(1, thief.getZone(ZoneType.Battlefield).getCards().size());

        AssertJUnit.assertTrue(game.restoreGameState());

        Card after = game.findById(creature.getId());
        AssertJUnit.assertNotNull(after);
        AssertJUnit.assertEquals("control goes back to the owner", owner, after.getController());
        AssertJUnit.assertEquals("and so does the creature", 1,
                owner.getZone(ZoneType.Battlefield).getCards().size());
        AssertJUnit.assertEquals("the thief has nothing left", 0,
                thief.getZone(ZoneType.Battlefield).getCards().size());
    }
}
