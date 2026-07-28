package forge.ai;

import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Every rewind point is also handed to whoever wants to put it on disk. Without that, a
 * game whose window dies is only reachable by attaching to the live process — which is
 * exactly the hole this closes.
 */
public class RewindAutosaveTest extends AITest {

    private void startTurn(Game game, Player p, int turn) {
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, p, turn);
        game.getPhaseHandler().setPriority(p);
    }

    @Test
    public void everyRewindPointIsOfferedForAutosaving() {
        final Game game = initAndCreateGame();
        final Player p = game.getPlayers().get(0);

        final List<Integer> turns = new ArrayList<>();
        final List<List<String>> texts = new ArrayList<>();
        game.setRewindAutosave((turn, text) -> {
            turns.add(turn);
            texts.add(text);
        });

        startTurn(game, p, 3);
        game.stashTurnRewindPoint(p);
        startTurn(game, p, 5);
        game.stashTurnRewindPoint(p);

        AssertJUnit.assertEquals("one autosave per rewind point", 2, turns.size());
        AssertJUnit.assertEquals(Integer.valueOf(3), turns.get(0));
        AssertJUnit.assertEquals(Integer.valueOf(5), turns.get(1));
        AssertJUnit.assertTrue("the text is the saved position",
                String.join("\n", texts.get(1)).contains("turn=5"));
    }

    @Test
    public void aFailingAutosaveDoesNotCostThePlayerTheirRewindPoint() {
        final Game game = initAndCreateGame();
        final Player p = game.getPlayers().get(0);

        game.setRewindAutosave((turn, text) -> {
            throw new RuntimeException("disk full");
        });

        startTurn(game, p, 3);
        game.stashTurnRewindPoint(p);

        AssertJUnit.assertEquals("the point is still there", 1, game.getAvailableRewindSteps(p));
    }

    @Test
    public void turnsThatTakeNoRewindPointAlsoWriteNothing() {
        final Game game = initAndCreateGame();
        final Player p = game.getPlayers().get(0);

        final List<Integer> turns = new ArrayList<>();
        game.setRewindAutosave((turn, text) -> turns.add(turn));

        startTurn(game, p, 3);
        game.stashTurnRewindPoint(p);
        game.stashTurnRewindPoint(p); // same turn again: already covered

        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS, p, 4);
        game.getPhaseHandler().setPriority(p);
        game.stashTurnRewindPoint(p); // wrong phase

        AssertJUnit.assertEquals(1, turns.size());
    }
}
