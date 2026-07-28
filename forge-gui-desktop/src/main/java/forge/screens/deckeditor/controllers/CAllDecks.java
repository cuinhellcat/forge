package forge.screens.deckeditor.controllers;

import forge.deck.DeckBase;
import forge.deck.DeckFormat;
import forge.deck.DeckProxy;
import forge.gui.framework.ICDoc;
import forge.item.InventoryItem;
import forge.itemmanager.DeckManager;
import forge.itemmanager.ItemManagerConfig;
import forge.screens.deckeditor.CDeckEditorUI;
import forge.screens.deckeditor.views.VAllDecks;

/**
 * Controls the "all decks" panel in the deck editor UI.
 *
 * <br><br><i>(C at beginning of class name denotes a control class.)</i>
 *
 */
public enum CAllDecks implements ICDoc {
    SINGLETON_INSTANCE;

    private final VAllDecks view = VAllDecks.SINGLETON_INSTANCE;

    //========== Overridden methods

    @Override
    public void register() {
    }

    /* (non-Javadoc)
     * @see forge.gui.framework.ICDoc#initialize()
     */
    @Override
    public void initialize() {
        refresh();
    }

    public void refresh() {
        refreshDeckManager(view.getLstDecks(), DeckProxy.getAllConstructedDecks());
    }

    /* (non-Javadoc)
     * @see forge.gui.framework.ICDoc#update()
     */
    @Override
    public void update() {
        updateDeckManager(view.getLstDecks());
    }

    public static void refreshDeckManager(DeckManager dm, Iterable<DeckProxy> deckList){
        dm.setPool(deckList);
    }

    public static void updateDeckManager(DeckManager dm){
        final ItemManagerConfig config = dm.getGameType().getDeckFormat() == DeckFormat.Commander
                ? ItemManagerConfig.COMMANDER_DECKS
                : ItemManagerConfig.CONSTRUCTED_DECKS;
        dm.setup(config);
        if (dm.getSelectedIndex() == 0) {
            // This may be default and so requiring potential update!
            ACEditorBase<? extends InventoryItem, ? extends DeckBase> editorCtrl =
                    CDeckEditorUI.SINGLETON_INSTANCE.getCurrentEditorController();
            // An editor exists well before its deck controller is wired up, and switching
            // screens runs this in between. Asking it for a deck name then threw, which
            // aborted the entire layout load and left the window with nothing in it.
            if (editorCtrl != null && editorCtrl.getDeckController() != null) {
                String currentDeckName = editorCtrl.getDeckController().getModelName();
                if (currentDeckName != null && currentDeckName.length() > 0) {
                    DeckProxy deckProxy = dm.stringToItem(currentDeckName);
                    // deckProxy on the left: having nothing selected is a normal state,
                    // and getSelectedItem() is null then.
                    if (deckProxy != null && !deckProxy.equals(dm.getSelectedItem()))
                        dm.setSelectedItem(deckProxy);
                }
            }
        }
    }
}
