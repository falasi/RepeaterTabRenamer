package burp.tabrenamer;

import burp.api.montoya.core.ToolType;
import burp.api.montoya.logging.Logging;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import burp.api.montoya.ui.contextmenu.InvocationType;
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse;

import javax.swing.JMenuItem;
import java.awt.Component;
import java.util.List;
import java.util.Optional;

/**
 * Adds a "Use selection as Repeater tab name" item to the right-click menu when text is
 * selected in a Repeater request/response editor.
 *
 * <p>Like every extension's context-menu items, Burp nests this under an "Extensions" submenu
 * rather than the top level — that's standard Burp UI grouping, not something an extension can
 * change. See {@link RepeaterSelectionHotKeyHandler} for a way to trigger the same action
 * without going through the menu at all.
 */
public final class RepeaterSelectionContextMenuProvider implements ContextMenuItemsProvider {

    private final TabNameGenerator nameGenerator;
    private final RepeaterTabTitler tabTitler;
    private final Logging logging;

    public RepeaterSelectionContextMenuProvider(TabNameGenerator nameGenerator, RepeaterTabTitler tabTitler, Logging logging) {
        this.nameGenerator = nameGenerator;
        this.tabTitler = tabTitler;
        this.logging = logging;
    }

    @Override
    public List<Component> provideMenuItems(ContextMenuEvent event) {
        if (!event.isFromTool(ToolType.REPEATER)) {
            return List.of();
        }
        // Repeater's response pane (and depending on Burp version, sometimes the request pane
        // too) is reported as a "viewer" invocation, not an "editor" one, even though it's the
        // same Repeater UI — so both variants need to be accepted here.
        if (!event.isFrom(InvocationType.MESSAGE_EDITOR_REQUEST, InvocationType.MESSAGE_EDITOR_RESPONSE,
                InvocationType.MESSAGE_VIEWER_REQUEST, InvocationType.MESSAGE_VIEWER_RESPONSE)) {
            return List.of();
        }

        Optional<MessageEditorHttpRequestResponse> editorOpt = event.messageEditorRequestResponse();
        if (editorOpt.isEmpty()) {
            return List.of();
        }

        String selectedText = EditorSelection.extractSelectedText(editorOpt.get());
        if (selectedText == null || selectedText.isBlank()) {
            return List.of();
        }

        JMenuItem item = new JMenuItem("Use selection as Repeater tab name");
        item.addActionListener(e -> {
            try {
                tabTitler.renameActiveTab(nameGenerator.sanitizeForTabName(selectedText), true);
            } catch (Exception ex) {
                logging.logToError("repeater-tab-renamer: failed to rename tab from selection", ex);
            }
        });
        return List.of(item);
    }
}
