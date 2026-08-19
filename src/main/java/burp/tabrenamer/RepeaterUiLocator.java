package burp.tabrenamer;

import burp.api.montoya.logging.Logging;

import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.KeyboardFocusManager;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Everything in this extension that depends on Burp's internal Swing structure, in one class.
 *
 * <p>It exists because Montoya can only name a Repeater tab that the extension itself created
 * ({@code Repeater.sendToRepeater(request, name)}); there is no supported call to retitle an
 * existing tab, which is the common case (a tab made by "Send to Repeater" from Proxy or
 * Target). The workaround is to find Repeater's request {@link JTabbedPane} by walking the
 * component tree.
 *
 * <p>Kept deliberately separate from {@link RepeaterTabTitler}, which holds the naming policy:
 * if a future Montoya release exposes native tab renaming, this file is the only one that has
 * to go, and the policy and its tests survive untouched.
 *
 * <p>Defensive by construction, because the structure it reads is undocumented and may change:
 * <ul>
 *   <li>Every public method is EDT-only and returns a "found nothing" value off the EDT, so no
 *       Swing state is ever touched from Burp's HTTP or extension threads.</li>
 *   <li>Nothing throws. A different-looking hierarchy yields an empty result and the caller
 *       simply doesn't rename, which leaves Burp exactly as it was.</li>
 *   <li>Searches are breadth-first and depth-bounded, so an unexpectedly deep tree can't turn
 *       a lookup into a long EDT stall.</li>
 *   <li>Candidate tab strips are rejected unless they look like request tabs, and are confirmed
 *       to sit inside Repeater whenever Repeater itself can be located.</li>
 *   <li>The only cached component is held through a {@link WeakReference}, so nothing here
 *       keeps a piece of Burp's UI alive.</li>
 * </ul>
 */
final class RepeaterUiLocator {

    /** Sub-tabs inside a message editor — never the request tab strip. */
    private static final Set<String> EDITOR_SUBTAB_TITLES = Set.of(
            "pretty", "raw", "hex", "render", "json beautifier", "inspector"
    );

    /** The main tool switcher's tabs — never the request tab strip. */
    private static final Set<String> KNOWN_TOOL_TAB_TITLES = Set.of(
            "dashboard", "target", "proxy", "intruder", "repeater", "sequencer",
            "decoder", "comparer", "logger", "organizer", "extensions", "learn",
            "settings", "recorded login replayer", "collaborator", "extender"
    );

    private static final String REPEATER_TAB_TITLE = "repeater";

    /** Depth bound for the frame-wide search, so a deep UI tree can't become a long scan. */
    private static final int MAX_SEARCH_DEPTH = 25;

    private final Frame suiteFrame;
    private final Logging logging;

    /**
     * Cache of Repeater's own tool component, so confirming that a tab strip belongs to Repeater
     * doesn't re-scan the frame on every request. Weak so this never keeps UI alive, and
     * revalidated on each use because Burp can rebuild the component (e.g. detaching Repeater
     * into its own window).
     *
     * <p>Read and written only on the EDT, with one exception: {@link #forget()} runs on
     * whichever thread unloads the extension. Volatile so that write is safely published; the
     * value is a plain reference swap, so no further locking is needed.
     */
    private volatile WeakReference<Component> cachedRepeaterTool = new WeakReference<>(null);

    RepeaterUiLocator(Frame suiteFrame, Logging logging) {
        this.suiteFrame = suiteFrame;
        this.logging = logging;
    }

    /**
     * The Repeater request tab strip containing the component that currently has keyboard focus,
     * or null if focus isn't inside one.
     *
     * <p>Focus is the anchor because it's the only signal available that identifies which tab the
     * user means, and it's what Burp itself uses to decide where a hotkey applies. Walking
     * outward from it also bounds the search naturally.
     */
    JTabbedPane focusedRequestTabStrip() {
        if (!SwingUtilities.isEventDispatchThread()) {
            return null;
        }
        try {
            Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            if (focusOwner == null) {
                return null;
            }

            Container current = (focusOwner instanceof Container container) ? container : focusOwner.getParent();
            while (current != null && current != suiteFrame) {
                if (current instanceof JTabbedPane pane) {
                    if (isKnownToolTabStrip(pane)) {
                        // Walked all the way out to the main tool switcher (Proxy/Repeater/...)
                        // without finding a request tab strip — focus isn't in one, so stop
                        // rather than risk renaming the wrong thing.
                        return null;
                    }
                    if (isRequestTabStrip(pane) && belongsToRepeater(pane)) {
                        return pane;
                    }
                }
                current = current.getParent();
            }
            return null;
        } catch (Exception e) {
            logging.logToError("repeater-tab-renamer: could not locate the Repeater tab strip", e);
            return null;
        }
    }

    /**
     * Titles of Repeater's request tabs, located from the suite frame rather than from focus —
     * needed when naming a tab that doesn't exist yet while focus is still in Proxy or Intruder.
     *
     * <p>An empty list means "couldn't tell", and callers treat that as "no known duplicates".
     */
    List<String> repeaterTabTitles() {
        if (!SwingUtilities.isEventDispatchThread()) {
            return List.of();
        }
        try {
            Component repeaterTool = repeaterToolComponent();
            if (!(repeaterTool instanceof Container repeaterContainer)) {
                return List.of();
            }
            JTabbedPane requestStrip = findDescendant(repeaterContainer, this::isRequestTabStrip);
            return requestStrip == null ? List.of() : titlesOf(requestStrip, -1);
        } catch (Exception e) {
            logging.logToError("repeater-tab-renamer: could not read existing Repeater tab titles", e);
            return List.of();
        }
    }

    /** Titles of every tab in {@code pane} except one, for duplicate checks. */
    List<String> titlesOf(JTabbedPane pane, int excludedIndex) {
        List<String> titles = new ArrayList<>(pane.getTabCount());
        for (int i = 0; i < pane.getTabCount(); i++) {
            if (i != excludedIndex) {
                titles.add(pane.getTitleAt(i));
            }
        }
        return titles;
    }

    /** Drops the cached component; called when the extension unloads. */
    void forget() {
        cachedRepeaterTool = new WeakReference<>(null);
    }

    /**
     * Confirms a candidate strip really is Repeater's, so an auto-rename can never retitle a
     * lookalike tab strip in another tool.
     *
     * <p>When Repeater itself can't be found in the suite frame the check passes rather than
     * fails: Burp lets a tool be detached into its own window, and refusing to rename in that
     * case would break the feature for those users. The other guards (not the tool switcher,
     * not editor sub-tabs, focus-anchored) still apply, so this is a narrowing of the previous
     * behavior, never a widening.
     */
    private boolean belongsToRepeater(Component candidate) {
        Component repeaterTool = repeaterToolComponent();
        if (repeaterTool == null) {
            return true;
        }
        for (Component current = candidate; current != null; current = current.getParent()) {
            if (current == repeaterTool) {
                return true;
            }
        }
        return false;
    }

    /** The component behind the main window's "Repeater" tool tab, or null if not found. */
    private Component repeaterToolComponent() {
        Component cached = cachedRepeaterTool.get();
        if (cached != null && cached.getParent() != null) {
            return cached;
        }
        if (suiteFrame == null) {
            return null;
        }
        JTabbedPane toolStrip = findDescendant(suiteFrame, this::isKnownToolTabStrip);
        Component repeaterTool = toolStrip == null ? null : componentOfTab(toolStrip, REPEATER_TAB_TITLE);
        cachedRepeaterTool = new WeakReference<>(repeaterTool);
        return repeaterTool;
    }

    /** Breadth-first so the outermost match wins, and depth-bounded to stay cheap. */
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
}
