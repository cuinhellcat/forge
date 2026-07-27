package forge.ai;

import forge.game.Game;
import forge.game.GameState;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.util.Arrays;

/**
 * A card on an Adventure waits in exile and its creature half stays castable. Loading a
 * state used to lose that: the permission effect was created while the exile zone was
 * being read, and setting up the command zone right afterwards wiped it again.
 */
public class AdventureGameStateTest extends AITest {

    private void applyOnGameThread(final Game game, final String text) {
        final Thread t = new Thread(() -> {
            final GameState in = new GameState();
            in.parse(Arrays.asList(text.split("\n")));
            in.applyToGame(game);
        }, "Game-test");
        t.start();
        try {
            t.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private SpellAbility creatureHalf(Game game, Player p) {
        for (Card c : p.getZone(ZoneType.Exile).getCards()) {
            for (SpellAbility sa : c.getAllPossibleAbilities(p, false)) {
                if (!sa.isAdventure()) {
                    return sa;
                }
            }
        }
        return null;
    }

    @Test
    public void adventureCardStaysCastableAfterLoadingAState() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, p, 3);
        game.getPhaseHandler().setPriority(p);

        addCardToZone("Callous Sell-Sword", p, ZoneType.Exile);

        final GameState written = new GameState();
        written.initFromGame(game);
        final String text = written.toString();
        AssertJUnit.assertTrue("the state records the adventure", text.contains("OnAdventure"));

        applyOnGameThread(game, text);

        Player after = game.getPlayers().get(0);
        AssertJUnit.assertEquals("the card is back in exile", 1, after.getZone(ZoneType.Exile).size());
        AssertJUnit.assertNotNull("and its creature half can be cast from there", creatureHalf(game, after));

        int reminders = 0;
        for (Card c : after.getZone(ZoneType.Command).getCards()) {
            if (c.getName().endsWith("'s Adventure")) {
                reminders++;
            }
        }
        AssertJUnit.assertEquals("exactly one reminder in the command zone", 1, reminders);
    }
}
