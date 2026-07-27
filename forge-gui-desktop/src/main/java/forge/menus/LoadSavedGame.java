package forge.menus;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.SwingUtilities;

import forge.gui.GuiBase;
import forge.deck.Deck;
import forge.game.GameRules;
import forge.game.GameState;
import forge.game.GameType;
import forge.game.player.RegisteredPlayer;
import forge.gamemodes.match.HostedMatch;
import forge.gui.SOverlayUtils;
import forge.localinstance.properties.ForgeConstants;
import forge.player.GamePlayerUtil;
import forge.toolbox.FSkin.SkinnedMenuItem;
import forge.util.Localizer;
import forge.util.FileUtil;
import forge.gui.util.SOptionPane;

/**
 * Starts a match from a game saved during play (Game menu &gt; Save game).
 *
 * A saved state carries everything the position needs, libraries included, so the match is
 * started with empty decks and no opening hand — the same way puzzles are set up — and the
 * state is applied once the game exists.
 */
public final class LoadSavedGame {
    private static final Pattern PLAYER_KEY = Pattern.compile("^p(\\d+)life=", Pattern.MULTILINE);
    /** What Forge uses when nobody says otherwise — see RegisteredPlayer.startingHand. */
    private static final int DEFAULT_MAX_HAND_SIZE = 7;

    private LoadSavedGame() { }

    public static SkinnedMenuItem getMenuItem() {
        final SkinnedMenuItem item = new SkinnedMenuItem(Localizer.getInstance().getMessage("lblLoadSavedGame"));
        item.addActionListener(e -> load());
        return item;
    }

    private static void load() {
        final Localizer localizer = Localizer.getInstance();
        final File dir = new File(ForgeConstants.USER_GAMES_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        final String filename = GuiBase.getInterface().showFileDialog(
                localizer.getMessage("lblLoadSavedGame"), ForgeConstants.USER_GAMES_DIR);
        if (filename == null) {
            return;
        }

        final GameState state = new GameState();
        final int playerCount;
        try {
            playerCount = countPlayers(filename);
            try (FileInputStream in = new FileInputStream(filename)) {
                state.parse(in);
            }
        } catch (final Exception e) {
            SOptionPane.showErrorDialog(localizer.getMessage("lblErrorLoadingBattleSetupFile") + "\n" + filename);
            return;
        }
        if (playerCount < 2) {
            SOptionPane.showErrorDialog(localizer.getMessage("lblSavedGameUnreadable") + "\n" + filename);
            return;
        }

        SwingUtilities.invokeLater(() -> {
            SOverlayUtils.startGameOverlay();
            SOverlayUtils.showOverlay();
        });

        final HostedMatch hostedMatch = GuiBase.getInterface().hostMatch();
        hostedMatch.setStartGameHook(() -> {
            state.applyToGame(hostedMatch.getGame());
            // Starting the match with no opening hand also sets the maximum hand size to
            // zero (Game.java: setMaxHandSize(startingHand)), and a saved state does not
            // carry that number, so cleanup would ask the player to discard their hand.
            for (final forge.game.player.Player p : hostedMatch.getGame().getPlayers()) {
                p.setMaxHandSize(DEFAULT_MAX_HAND_SIZE);
                p.setStartingHandSize(DEFAULT_MAX_HAND_SIZE);
            }
        });

        // Empty decks and no opening hand: everything comes from the saved state. The first
        // player is the one who saved, the rest are AI, matching how the file numbers them.
        final List<RegisteredPlayer> players = new ArrayList<>();
        final RegisteredPlayer human = new RegisteredPlayer(new Deck()).setPlayer(GamePlayerUtil.getGuiPlayer());
        human.setStartingHand(0);
        players.add(human);
        for (int i = 1; i < playerCount; i++) {
            final RegisteredPlayer ai = new RegisteredPlayer(new Deck()).setPlayer(GamePlayerUtil.createAiPlayer());
            ai.setStartingHand(0);
            players.add(ai);
        }

        // Puzzle rules, because loading a position is what they are for: no mulligan over the
        // empty opening hand, and the first player is taken rather than diced for. The saved
        // state decides whose turn it is anyway.
        final GameRules rules = new GameRules(GameType.Puzzle);
        rules.setGamesPerMatch(1);
        hostedMatch.startMatch(rules, null, players, human, GuiBase.getInterface().getNewGuiGame());

        SwingUtilities.invokeLater(SOverlayUtils::hideOverlay);
    }

    /** How many players the file describes, counted from its "pNlife=" lines. */
    private static int countPlayers(String filename) {
        final String text = String.join("\n", FileUtil.readFile(filename));
        final Matcher m = PLAYER_KEY.matcher(text);
        int highest = -1;
        while (m.find()) {
            highest = Math.max(highest, Integer.parseInt(m.group(1)));
        }
        return highest + 1;
    }
}
