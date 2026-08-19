package burp.tabrenamer;

import burp.api.montoya.core.ToolType;
import burp.api.montoya.logging.Logging;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import burp.api.montoya.ui.contextmenu.InvocationType;
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse;

import javax.swing.JMenuItem;
import java.awt.Component;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Adds a "Use selection as Repeater tab name" item to the right-click menu when text is
 * selected in a Repeater request/response editor, plus — when the user has staged parts with
 * the numbered hotkeys — an item that applies them and shows what they are. Staged parts are
 * otherwise invisible in the UI, so this doubles as the way to see and discard them without
 * having to read the extension's Output tab.
 *
 * <p>Like every extension's context-menu items, Burp nests these under an "Extensions" submenu
 * rather than the top level — that's standard Burp UI grouping, not something an extension can
 * change. See {@link MessageEditorSelectionHotKeyHandler} for a way to trigger the same actions
 * without going through the menu at all.
 */
public final class RepeaterSelectionContextMenuProvider implements ContextMenuItemsProvider {

    private final TabNameGenerator nameGenerator;
    private final RepeaterTabTitler tabTitler;
    private final TabNamePartStore partStore;
    private final Logging logging;

    public RepeaterSelectionContextMenuProvider(TabNameGenerator nameGenerator, RepeaterTabTitler tabTitler,
                                                TabNamePartStore partStore, Logging logging) {
        this.nameGenerator = nameGenerator;
        this.tabTitler = tabTitler;
        this.partStore = partStore;
        this.logging = logging;
    }

    @Override
    public List<Component> provideMenuItems(ContextMenuEvent event) {
        // Burp builds its context menu by calling this; letting anything escape would break the
        // menu for every tool, so the whole method is guarded and degrades to "no extra items".
        try {
            return menuItems(event);
        } catch (Exception e) {
            logging.logToError("repeater-tab-renamer: failed to build context menu items", e);
            return List.of();
        }
    }

    private List<Component> menuItems(ContextMenuEvent event) {
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

        List<Component> items = new ArrayList<>(2);

        String selectedText = EditorSelection.extractSelectedText(editorOpt.get());
        if (selectedText != null && !selectedText.isBlank()) {
            items.add(selectionItem(selectedText));
        }

        // Menus are built on the EDT, which is where the tab identity has to be read from.
        Object tabKey = tabTitler.activeTabKey();
        List<String> parts = partStore.partsFor(tabKey);
        if (!parts.isEmpty()) {
            items.add(stagedPartsItem(tabKey, parts));
        }

        return items;
    }

    private JMenuItem selectionItem(String selectedText) {
        JMenuItem item = new JMenuItem("Use selection as Repeater tab name");
        item.addActionListener(e -> {
            try {
                tabTitler.renameActiveTab(nameGenerator.sanitizeForTabName(selectedText), true);
            } catch (Exception ex) {
                logging.logToError("repeater-tab-renamer: failed to rename tab from selection", ex);
            }
        });
        return item;
    }

    private JMenuItem stagedPartsItem(Object tabKey, List<String> parts) {
        // Weak so a menu item Burp happens to retain can't keep a closed Repeater tab's
        // component alive; a collected key just means there are no parts left to clear.
        WeakReference<Object> tabRef = new WeakReference<>(tabKey);
        JMenuItem item = new JMenuItem("Rename Repeater tab: " + nameGenerator.joinParts(parts));
        item.addActionListener(e -> {
            try {
                tabTitler.renameActiveTab(nameGenerator.joinParts(parts), true);
                partStore.clear(tabRef.get());
            } catch (Exception ex) {
                logging.logToError("repeater-tab-renamer: failed to rename tab from staged parts", ex);
            }
        });
        return item;
    }
}
