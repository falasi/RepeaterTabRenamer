package burp.tabrenamer;

import burp.api.montoya.logging.Logging;

import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.KeyboardFocusManager;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Renames the currently focused Repeater request tab.
 *
 * <p>Montoya's public API only lets an extension name a tab it creates itself via
 * {@code Repeater.sendToRepeater(request, name)} — there is no supported way to rename a tab
 * that already exists (e.g. one created by "Send to Repeater" from Proxy/Target). To handle
 * that case we walk the Swing hierarchy from the current keyboard-focus owner outward looking
 * for the {@link JTabbedPane} that holds the Repeater request tabs, and retitle its selected
 * tab. This relies on Burp's internal UI structure rather than a documented API, so it's a
 * best-effort heuristic that may need adjusting if a future Burp release changes that structure.
 *
 * <p>Having that tab strip in hand also makes it the natural place to keep generated names
 * distinct: the live tab titles are the authoritative answer to "is this name already taken",
 * so no separate bookkeeping is needed and closing or hand-renaming a tab needs no cleanup.
 */
public final class RepeaterTabTitler {

    private static final Set<String> EDITOR_SUBTAB_TITLES = Set.of(
            "pretty", "raw", "hex", "render", "json beautifier", "inspector"
    );

    private static final Set<String> KNOWN_TOOL_TAB_TITLES = Set.of(
            "dashboard", "target", "proxy", "intruder", "repeater", "sequencer",
            "decoder", "comparer", "logger", "organizer", "extensions", "learn",
            "settings", "recorded login replayer", "collaborator", "extender"
    );

    /** Depth bound on the frame-wide search, so a deep UI tree can't turn into a long scan. */
    private static final int MAX_SEARCH_DEPTH = 25;

    private final Frame suiteFrame;
    private final TabNameGenerator nameGenerator;
    private final Logging logging;

    public RepeaterTabTitler(Frame suiteFrame, TabNameGenerator nameGenerator, Logging logging) {
        this.suiteFrame = suiteFrame;
        this.nameGenerator = nameGenerator;
        this.logging = logging;
    }

    /**
     * Auto-naming entry point: only overwrites a tab whose title still looks auto-generated,
     * so an existing manual (or user-selection-based) rename is never clobbered. Safe to call
     * from any thread; the actual UI work is dispatched onto the EDT.
     */
    public void renameActiveTab(String newName) {
        renameActiveTab(newName, false);
    }

    /**
     * @param force when true (an explicit user action, e.g. "use selection as tab name"),
     *              overwrites the tab's title regardless of its current value.
     */
    public void renameActiveTab(String newName, boolean force) {
        SwingUtilities.invokeLater(() -> renameActiveTabOnEdt(newName, force));
    }

    /**
     * As {@link #renameActiveTab(String, boolean)}, but for callers already on the EDT that
     * need the rename to happen in the same event as their own lookup of
     * {@link #activeTabKey()} — otherwise focus could move in between and the rename could
     * land on a different tab than the one whose staged parts were read.
     */
    void renameActiveTabOnEdt(String newName, boolean force) {
        try {
            doRename(newName, force);
        } catch (Exception e) {
            logging.logToError("repeater-tab-renamer: failed to rename tab", e);
        }
    }

    /**
     * Identity of the Repeater tab that a rename right now would target, for callers that need
     * to associate state with "this tab" (see {@link TabNamePartStore}). The Swing component
     * behind the selected tab is used because it stays the same object for that tab's whole
     * life, whereas its index shifts as tabs are closed or dragged.
     *
     * <p>Must be called on the EDT; returns null off it, or when the Repeater tab strip can't
     * be located from the current focus.
     */
    Object activeTabKey() {
        if (!SwingUtilities.isEventDispatchThread()) {
            return null;
        }
        JTabbedPane pane = findRequestTabStripFromFocus();
        if (pane == null || pane.getSelectedIndex() < 0) {
            return null;
        }
        return pane.getComponentAt(pane.getSelectedIndex());
    }

    /**
     * The titles of Repeater's request tabs, found from the suite frame rather than from focus —
     * used by {@link SendToRepeaterHotKeyHandler}, which names a not-yet-created tab while focus
     * is still in Proxy or Intruder, so the focus walk would never reach Repeater.
     *
     * <p>Returns an empty list off the EDT or if the strip can't be found, which callers treat
     * as "no known duplicates": the name is used as-is, exactly as before this existed.
     */
    List<String> repeaterTabTitles() {
        if (!SwingUtilities.isEventDispatchThread() || suiteFrame == null) {
            return List.of();
        }
        try {
            JTabbedPane toolStrip = findDescendant(suiteFrame, this::isKnownToolTabStrip);
            if (toolStrip == null) {
                return List.of();
            }
            Component repeaterTool = componentOfTab(toolStrip, "repeater");
            if (!(repeaterTool instanceof Container repeaterContainer)) {
                return List.of();
            }
            JTabbedPane requestStrip = findDescendant(repeaterContainer, this::isRequestTabStrip);
            return requestStrip == null ? List.of() : titlesOf(requestStrip, -1);
        } catch (Exception e) {
            logging.logToError("repeater-tab-renamer: failed to read existing Repeater tab titles", e);
            return List.of();
        }
    }

    private void doRename(String newName, boolean force) {
        JTabbedPane pane = findRequestTabStripFromFocus();
        if (pane != null) {
            renameSelectedTab(pane, newName, force);
        }
    }

    /**
     * Walks outward from the focus owner. Reaching the main tool switcher (Proxy/Repeater/...)
     * without having found a request tab strip means focus isn't inside one, so we stop rather
     * than risk renaming the wrong thing.
     */
    private JTabbedPane findRequestTabStripFromFocus() {
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (focusOwner == null) {
            return null;
        }

        Container current = (focusOwner instanceof Container) ? (Container) focusOwner : focusOwner.getParent();
        while (current != null && current != suiteFrame) {
            if (current instanceof JTabbedPane pane) {
                if (isKnownToolTabStrip(pane)) {
                    return null;
                }
                if (isRequestTabStrip(pane)) {
                    return pane;
                }
            }
            current = current.getParent();
        }
        return null;
    }

    /** Breadth-first so the outermost matching strip wins, and depth-bounded to stay cheap. */
    private JTabbedPane findDescendant(Container root, Predicate<JTabbedPane> matches) {
        Deque<Container> queue = new ArrayDeque<>();
        Deque<Integer> depths = new ArrayDeque<>();
        queue.add(root);
        depths.add(0);
        while (!queue.isEmpty()) {
            Container container = queue.removeFirst();
            int depth = depths.removeFirst();
            if (container instanceof JTabbedPane pane && matches.test(pane)) {
                return pane;
            }
            if (depth >= MAX_SEARCH_DEPTH) {
                continue;
            }
            for (Component child : container.getComponents()) {
                if (child instanceof Container childContainer) {
                    queue.addLast(childContainer);
                    depths.addLast(depth + 1);
                }
            }
        }
        return null;
    }

    private Component componentOfTab(JTabbedPane pane, String title) {
        for (int i = 0; i < pane.getTabCount(); i++) {
            String tabTitle = pane.getTitleAt(i);
            if (tabTitle != null && tabTitle.trim().equalsIgnoreCase(title)) {
                return pane.getComponentAt(i);
            }
        }
        return null;
    }

    private boolean isRequestTabStrip(JTabbedPane pane) {
        return pane.getTabCount() > 0
                && !allTitlesIn(pane, EDITOR_SUBTAB_TITLES)
                && !allTitlesIn(pane, KNOWN_TOOL_TAB_TITLES);
    }

    private boolean isKnownToolTabStrip(JTabbedPane pane) {
        return pane.getTabCount() > 0 && allTitlesIn(pane, KNOWN_TOOL_TAB_TITLES);
    }

    private boolean allTitlesIn(JTabbedPane pane, Set<String> candidates) {
        for (int i = 0; i < pane.getTabCount(); i++) {
            String title = pane.getTitleAt(i);
            if (title == null || !candidates.contains(title.trim().toLowerCase())) {
                return false;
            }
        }
        return true;
    }

    private void renameSelectedTab(JTabbedPane pane, String newName, boolean force) {
        int index = pane.getSelectedIndex();
        if (index < 0) {
            return;
        }
        if (!force && !isAutoGeneratedTitle(pane.getTitleAt(index))) {
            return;
        }
        // Compared against every other tab, but not this one: re-sending in a tab that already
        // holds the name must not turn "api-users" into "api-users-2" on each send.
        pane.setTitleAt(index, nameGenerator.uniqueAmong(newName, titlesOf(pane, index)));
    }

    private List<String> titlesOf(JTabbedPane pane, int excludedIndex) {
        List<String> titles = new ArrayList<>(pane.getTabCount());
        for (int i = 0; i < pane.getTabCount(); i++) {
            if (i != excludedIndex) {
                titles.add(pane.getTitleAt(i));
            }
        }
        return titles;
    }

    /** Only overwrite titles that still look auto-generated, so a manual rename is never clobbered. */
    private boolean isAutoGeneratedTitle(String currentTitle) {
        if (currentTitle == null) {
            return true;
        }
        String trimmed = currentTitle.trim();
        return trimmed.isEmpty() || trimmed.matches("\\d+") || trimmed.matches("(?i)untitled.*");
    }
}
