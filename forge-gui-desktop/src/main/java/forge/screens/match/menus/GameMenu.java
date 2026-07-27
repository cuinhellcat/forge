package forge.screens.match.menus;

import java.awt.event.KeyEvent;
import java.util.List;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

import forge.control.KeyboardShortcuts;
import forge.gamemodes.match.DrawOfferMessage;
import forge.gamemodes.match.YieldController;
import forge.gamemodes.match.YieldUpdate;
import forge.localinstance.properties.ForgePreferences;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import forge.player.PlayerControllerHuman;
import forge.screens.match.CMatchUI;
import forge.screens.match.VAutoYieldsAndTriggers;
import forge.screens.match.VYieldSettings;
import forge.toolbox.FSkin.SkinnedCheckBoxMenuItem;
import forge.toolbox.FSkin.SkinnedMenuItem;
import forge.util.Localizer;

/**
 * Returns a JMenu containing core in-game actions for the current match.
 */
public final class GameMenu {
    private static final ForgePreferences prefs = FModel.getPreferences();
    private final CMatchUI matchUI;
    public GameMenu(final CMatchUI matchUI) {
        this.matchUI = matchUI;
    }

    public JMenu getMenu() {
        final Localizer localizer = Localizer.getInstance();
        final JMenu menu = new JMenu(localizer.getMessage("lblGame"));
        menu.setMnemonic(KeyEvent.VK_G);
        menu.add(getMenuItem_Undo());
        final JMenu rewindMenu = getMenu_Rewind();
        if (rewindMenu != null) {
            menu.add(rewindMenu);
        }
        final SkinnedMenuItem saveItem = getMenuItem_SaveGame();
        final SkinnedMenuItem loadItem = getMenuItem_LoadGame();
        if (saveItem != null) {
            menu.addSeparator();
            menu.add(saveItem);
            menu.add(loadItem);
            menu.addSeparator();
        }
        menu.add(getMenuItem_Concede());
        menu.add(getMenuItem_OfferDraw());
        menu.add(getMenuItem_EndTurn());
        menu.add(getMenuItem_AlphaStrike());
        menu.addSeparator();
        menu.add(getMenuItem_ViewDeckList());
        menu.addSeparator();
        menu.add(getMenuItem_AutoYieldsAndTriggers());
        menu.add(getMenuItem_YieldSettings());
        final SkinnedCheckBoxMenuItem autoPassItem = getMenuItem_AutoPass();
        menu.add(autoPassItem);
        menu.add(getMenuItem_ClearRememberedAbilityOrders());
        menu.addMenuListener(new MenuListener() {
            @Override public void menuSelected(final MenuEvent e) {
                autoPassItem.setState(prefs.getPrefBoolean(FPref.YIELD_AUTO_PASS_NO_ACTIONS));
                if (rewindMenu != null) {
                    // Label every step with where it actually leads, and grey out the ones
                    // that have no rewind point behind them.
                    final PlayerControllerHuman controller =
                            (PlayerControllerHuman) matchUI.getGameController();
                    final List<String> points = controller.describeRewindPoints();
                    final Localizer loc = Localizer.getInstance();
                    rewindMenu.setEnabled(!points.isEmpty());
                    for (int i = 0; i < rewindMenu.getItemCount(); i++) {
                        final JMenuItem item = rewindMenu.getItem(i);
                        final String steps = i == 0
                                ? loc.getMessage("lblRewindOneAction")
                                : loc.getMessage("lblRewindManyActions", i + 1);
                        if (i < points.size()) {
                            item.setEnabled(true);
                            item.setText(loc.getMessage("lblRewindPointAt", steps, points.get(i)));
                        } else {
                            item.setEnabled(false);
                            item.setText(loc.getMessage("lblRewindPointAt", steps,
                                    loc.getMessage("lblRewindNoPoints")));
                        }
                    }
                }
            }
            @Override public void menuDeselected(final MenuEvent e) {}
            @Override public void menuCanceled(final MenuEvent e) {}
        });
        return menu;
    }

    private SkinnedMenuItem getMenuItem_ClearRememberedAbilityOrders() {
        final Localizer localizer = Localizer.getInstance();
        final SkinnedMenuItem menuItem = new SkinnedMenuItem(localizer.getMessage("lblResetSavedAbilityOrders"));
        menuItem.addActionListener(e -> matchUI.getGameController().sendYieldUpdate(new YieldUpdate.ClearAbilityOrders()));
        return menuItem;
    }

    /**
     * Undoes the player's last actions and everything that followed them, including the
     * other players' and the AI's moves. Only offered to whoever runs the game (host or
     * single player) — a network client has no game state to rewind.
     *
     * One entry per step, so going back further than one action is a single click rather
     * than repeated rewinds, which is awkward once the AI has moved again in between.
     */
    private JMenu getMenu_Rewind() {
        if (!(matchUI.getGameController() instanceof PlayerControllerHuman controller)) {
            return null;
        }
        final int maxSteps = controller.getGame().REWIND_STEPS;
        if (maxSteps < 1) {
            return null;
        }
        final Localizer localizer = Localizer.getInstance();
        final JMenu menu = new JMenu(localizer.getMessage("lblRewind"));
        for (int step = 1; step <= maxSteps; step++) {
            final int steps = step;
            final SkinnedMenuItem item = new SkinnedMenuItem(steps == 1
                    ? localizer.getMessage("lblRewindOneAction")
                    : localizer.getMessage("lblRewindManyActions", steps));
            item.addActionListener(e -> controller.requestRewind(steps));
            menu.add(item);
        }
        return menu;
    }

    /** Writes the current position to a file, for picking it up again after a crash. */
    private SkinnedMenuItem getMenuItem_SaveGame() {
        if (!(matchUI.getGameController() instanceof PlayerControllerHuman controller)) {
            return null;
        }
        final SkinnedMenuItem menuItem = new SkinnedMenuItem(Localizer.getInstance().getMessage("lblSaveGame"));
        menuItem.addActionListener(e -> controller.saveGameToFile());
        return menuItem;
    }

    private SkinnedMenuItem getMenuItem_LoadGame() {
        if (!(matchUI.getGameController() instanceof PlayerControllerHuman controller)) {
            return null;
        }
        final SkinnedMenuItem menuItem = new SkinnedMenuItem(Localizer.getInstance().getMessage("lblLoadGame"));
        menuItem.addActionListener(e -> controller.loadGameFromFile());
        return menuItem;
    }

    private SkinnedMenuItem getMenuItem_Undo() {
        final Localizer localizer = Localizer.getInstance();
        final SkinnedMenuItem menuItem = new SkinnedMenuItem(localizer.getMessage("lblUndo"));
        setAcceleratorFromPref(menuItem, FPref.SHORTCUT_UNDO);
        menuItem.addActionListener(e -> matchUI.getGameController().undoLastAction());
        return menuItem;
    }

    private SkinnedMenuItem getMenuItem_Concede() {
        final SkinnedMenuItem menuItem = new SkinnedMenuItem(matchUI.getConcedeCaption());
        setAcceleratorFromPref(menuItem, FPref.SHORTCUT_CONCEDE);
        menuItem.addActionListener(e -> matchUI.concede());
        return menuItem;
    }

    private SkinnedMenuItem getMenuItem_OfferDraw() {
        final Localizer localizer = Localizer.getInstance();
        final SkinnedMenuItem menuItem = new SkinnedMenuItem(localizer.getMessage("lblOfferDraw"));
        menuItem.addActionListener(e -> matchUI.getGameController().drawOfferAction(DrawOfferMessage.Action.OFFER));
        return menuItem;
    }

    private SkinnedMenuItem getMenuItem_EndTurn() {
        final Localizer localizer = Localizer.getInstance();
        final SkinnedMenuItem menuItem = new SkinnedMenuItem(localizer.getMessage("lblEndTurn"));
        setAcceleratorFromPref(menuItem, FPref.SHORTCUT_ENDTURN);
        menuItem.addActionListener(e -> YieldController.endTurn(matchUI.getGameController(), matchUI.getCurrentPlayer()));
        return menuItem;
    }

    private SkinnedMenuItem getMenuItem_AlphaStrike() {
        final Localizer localizer = Localizer.getInstance();
        final SkinnedMenuItem menuItem = new SkinnedMenuItem(localizer.getMessage("lblAlphaStrike"));
        setAcceleratorFromPref(menuItem, FPref.SHORTCUT_ALPHASTRIKE);
        menuItem.addActionListener(e -> matchUI.getGameController().alphaStrike());
        return menuItem;
    }

    private SkinnedMenuItem getMenuItem_ViewDeckList() {
        final Localizer localizer = Localizer.getInstance();
        final SkinnedMenuItem menuItem = new SkinnedMenuItem(localizer.getMessage("lblDeckList"));
        menuItem.addActionListener(e -> matchUI.viewDeckList());
        return menuItem;
    }

    private SkinnedMenuItem getMenuItem_AutoYieldsAndTriggers() {
        final Localizer localizer = Localizer.getInstance();
        final SkinnedMenuItem menuItem = new SkinnedMenuItem(localizer.getMessage("lblAutoYieldsAndTriggers"));
        menuItem.addActionListener(e -> new VAutoYieldsAndTriggers(matchUI).showDialog());
        return menuItem;
    }

    private SkinnedMenuItem getMenuItem_YieldSettings() {
        final Localizer localizer = Localizer.getInstance();
        final SkinnedMenuItem menuItem = new SkinnedMenuItem(localizer.getMessage("lblYieldSettings"));
        menuItem.addActionListener(e -> new VYieldSettings(matchUI).showDialog());
        return menuItem;
    }

    private SkinnedCheckBoxMenuItem getMenuItem_AutoPass() {
        final Localizer localizer = Localizer.getInstance();
        final SkinnedCheckBoxMenuItem menuItem = new SkinnedCheckBoxMenuItem(localizer.getMessage("lblEnableAutoPass"));
        setAcceleratorFromPref(menuItem, FPref.SHORTCUT_YIELD_AUTO_PASS);
        menuItem.setState(prefs.getPrefBoolean(FPref.YIELD_AUTO_PASS_NO_ACTIONS));
        menuItem.addActionListener(e -> {
            YieldController.toggleAutoPassNoActions(matchUI.getGameController());
            matchUI.getCDock().update();
            menuItem.setState(prefs.getPrefBoolean(FPref.YIELD_AUTO_PASS_NO_ACTIONS));
        });
        return menuItem;
    }

    /** Sets a menu item's accelerator display from a shortcut preference. */
    private static void setAcceleratorFromPref(final JMenuItem menuItem, final FPref pref) {
        final KeyStroke ks = KeyboardShortcuts.getKeyStrokeForPref(pref);
        if (ks != null) {
            menuItem.setAccelerator(ks);
        }
    }
}
