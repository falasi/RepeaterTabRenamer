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
 * Handles the "Use selection as Repeater tab name" hotkey, registered under
 * {@code HotKeyContext.HTTP_MESSAGE_EDITOR} — which fires for the focused pane in <em>any</em>
 * Burp message editor, not just Repeater's. That includes, notably, the request/response viewer
 * embedded in Proxy's HTTP history: clicking into it to select text moves focus off the history
 * table and into the editor itself, so a hotkey scoped only to
 * {@code HotKeyContext.PROXY_HTTP_HISTORY} (see {@link SendToRepeaterHotKeyHandler}) never fires
 * there — Burp reports that focus as the generic editor context instead.
 *
 * <p>Behavior adapts to where the focused message came from:
 * <ul>
 *   <li>From Repeater — renames the active tab, from the parts staged with
 *       {@link SelectionPartHotKeyHandler} if there are any, otherwise from the current
 *       selection (and does nothing if there's neither).</li>
 *   <li>From anywhere else (Proxy history, Intruder attack results, ...) — sends it to a new
 *       Repeater tab, named from the selection or auto-named if nothing is selected, via
 *       {@link SendToRepeaterHotKeyHandler#sendFromEditor}.</li>
 * </ul>
 *
 * <p>Staged parts take precedence over a live selection, and are consumed by the rename: the
 * user went out of their way to stage them, and consuming them means the very next press of
 * the same key is back to plain selection renaming with no state left behind.
 */
public final class MessageEditorSelectionHotKeyHandler implements HotKeyHandler {

    private final TabNameGenerator nameGenerator;
    private final RepeaterTabTitler tabTitler;
    private final TabNamePartStore partStore;
    private final SendToRepeaterHotKeyHandler sendHandler;
    private final Logging logging;

    public MessageEditorSelectionHotKeyHandler(TabNameGenerator nameGenerator, RepeaterTabTitler tabTitler,
                                               TabNamePartStore partStore, SendToRepeaterHotKeyHandler sendHandler,
                                               Logging logging) {
        this.nameGenerator = nameGenerator;
        this.tabTitler = tabTitler;
        this.partStore = partStore;
        this.sendHandler = sendHandler;
        this.logging = logging;
    }

    @Override
    public void handle(HotKeyEvent event) {
        try {
            Optional<MessageEditorHttpRequestResponse> editorOpt = event.messageEditorRequestResponse();
            if (editorOpt.isEmpty()) {
                return;
            }
            MessageEditorHttpRequestResponse editor = editorOpt.get();

            if (event.isFromTool(ToolType.REPEATER)) {
                renameFromEditor(editor);
            } else {
                sendHandler.sendFromEditor(editor);
            }
        } catch (Exception e) {
            logging.logToError("repeater-tab-renamer: failed to handle the rename hotkey", e);
        }
    }

    private void renameFromEditor(MessageEditorHttpRequestResponse editor) {
        String selectedText = EditorSelection.extractSelectedText(editor);
        // Reading the staged parts, deciding, and renaming all happen in one EDT event so they
        // can't disagree about which tab is active.
        SwingUtilities.invokeLater(() -> rename(selectedText));
    }

    private void rename(String selectedText) {
        try {
            Object tabKey = tabTitler.activeTabKey();
            List<String> parts = partStore.partsFor(tabKey);

            if (!parts.isEmpty()) {
                // Renaming succeeds silently: the tab title is the confirmation.
                tabTitler.renameActiveTabOnEdt(nameGenerator.joinParts(parts), true);
                partStore.clear(tabKey);
                return;
            }

            if (selectedText != null && !selectedText.isBlank()) {
                tabTitler.renameActiveTabOnEdt(nameGenerator.sanitizeForTabName(selectedText), true);
            }
        } catch (Exception e) {
            logging.logToError("repeater-tab-renamer: failed to rename tab from hotkey", e);
        }
    }
}
