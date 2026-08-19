package burp.tabrenamer;

import burp.api.montoya.core.ToolType;
import burp.api.montoya.logging.Logging;
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse;
import burp.api.montoya.ui.hotkey.HotKeyEvent;
import burp.api.montoya.ui.hotkey.HotKeyHandler;

import javax.swing.SwingUtilities;
import java.util.List;
import java.util.Optional;

/**
 * Stages the current selection as one numbered piece of a multi-part tab name: select "POST"
 * and press Ctrl+Alt+1, select "users" and press Ctrl+Alt+2, then press the rename hotkey to
 * get "POST-users". One instance is registered per slot.
 *
 * <p>Numbered slots rather than a single "append" key so a part can be corrected in place —
 * press Ctrl+Alt+2 again over a different selection and part 2 is replaced, no clearing
 * ritual. Pressing a slot's key with <em>nothing</em> selected clears just that slot, which
 * avoids spending a fourth hotkey on a "reset" command.
 *
 * <p>Staging is Repeater-only. Elsewhere the same editor hotkey context is already used to
 * send a message to a new named tab, and a part staged against a Proxy history row would have
 * no tab to belong to.
 */
public final class SelectionPartHotKeyHandler implements HotKeyHandler {

    private final int slot;
    private final TabNamePartStore partStore;
    private final RepeaterTabTitler tabTitler;
    private final Logging logging;

    public SelectionPartHotKeyHandler(int slot, TabNamePartStore partStore,
                                      RepeaterTabTitler tabTitler, Logging logging) {
        this.slot = slot;
        this.partStore = partStore;
        this.tabTitler = tabTitler;
        this.logging = logging;
    }

    @Override
    public void handle(HotKeyEvent event) {
        try {
            if (!event.isFromTool(ToolType.REPEATER)) {
                logging.logToOutput("repeater-tab-renamer: tab name parts can only be staged in Repeater.");
                return;
            }
            Optional<MessageEditorHttpRequestResponse> editorOpt = event.messageEditorRequestResponse();
            if (editorOpt.isEmpty()) {
                return;
            }

            String selectedText = EditorSelection.extractSelectedText(editorOpt.get());
            // The tab identity must be resolved on the EDT, and in the same event as the store
            // write, so a part can't be filed against a tab the user has already navigated away
            // from. The rename hotkey resolves it exactly the same way.
            SwingUtilities.invokeLater(() -> store(selectedText));
        } catch (Exception e) {
            logging.logToError("repeater-tab-renamer: failed to save tab name part " + slot, e);
        }
    }

    private void store(String selectedText) {
        try {
            Object tabKey = tabTitler.activeTabKey();
            partStore.setPart(tabKey, slot, selectedText);

            List<String> pending = partStore.partsFor(tabKey);
            if (selectedText == null || selectedText.isBlank()) {
                logging.logToOutput("repeater-tab-renamer: cleared tab name part " + slot
                        + " (nothing selected). " + describe(pending));
            } else {
                logging.logToOutput("repeater-tab-renamer: tab name part " + slot + " = \""
                        + selectedText.trim() + "\". " + describe(pending));
            }
        } catch (Exception e) {
            logging.logToError("repeater-tab-renamer: failed to save tab name part " + slot, e);
        }
    }

    private String describe(List<String> pending) {
        if (pending.isEmpty()) {
            return "No parts staged for this tab.";
        }
        return "Staged for this tab: " + String.join(" | ", pending)
                + " — press the rename hotkey to apply.";
    }
}
