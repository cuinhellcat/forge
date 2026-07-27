package forge.ai;

import forge.game.Game;
import forge.game.GameActionUtil;
import forge.game.card.Card;
import forge.game.cost.CostPayment;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

/**
 * Rolling back a cast via the snapshot has to clean up the ability as well: the snapshot
 * holds cards, zones and mana, but not what the player announced, targeted, or the frozen
 * stack. Leaving those behind is what #10049 (spell can no longer be cast after a cancel)
 * and #8762 (cancelling freezes the game) look like.
 */
public class RollbackCleanupTest extends AITest {

    @Test
    public void snapshotRollbackClearsWhatTheSnapshotDoesNotHold() {
        Game game = initAndCreateGame();
        game.EXPERIMENTAL_RESTORE_SNAPSHOT = true;
        Player p = game.getPlayers().get(0);
        game.getPhaseHandler().setPriority(p);

        Card card = addCardToZone("Shock", p, ZoneType.Hand);
        SpellAbility sa = card.getFirstSpellAbility();
        sa.setActivatingPlayer(p);

        game.stashGameState();

        // What casting leaves on the ability and the card.
        sa.setXManaCostPaid(3);
        card.setCastSA(sa);
        card.setCastFrom(p.getZone(ZoneType.Hand));
        game.getStack().freezeStack(sa);
        AssertJUnit.assertTrue(game.getStack().isFrozen());

        GameActionUtil.rollbackAbility(sa, p.getZone(ZoneType.Hand), 0,
                new CostPayment(sa.getPayCosts(), sa), card);

        Card after = game.getCardState(card, null);
        AssertJUnit.assertNotNull(after);
        AssertJUnit.assertNull("announced X is cleared", sa.getXManaCostPaid());
        AssertJUnit.assertNull("card is no longer marked as being cast", after.getCastSA());
        AssertJUnit.assertNull("and no longer remembers where from", after.getCastFrom());
        AssertJUnit.assertFalse("the stack is unfrozen again", game.getStack().isFrozen());
    }
}
