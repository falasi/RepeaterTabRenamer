package burp.tabrenamer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Holds the selections a user has staged for a multi-part tab name, scoped per Repeater tab.
 *
 * <p>Scoping matters: staging "POST" in one tab and then switching to another must not leave
 * that other tab's rename shortcut holding a stray part. Montoya has no tab handle to key on,
 * so the key is the Swing component the Repeater tab strip holds for that tab (supplied by
 * {@link RepeaterTabTitler#activeTabKey()}) — a stable identity for the life of the tab that
 * survives tabs being reordered or others being closed, unlike a tab index would.
 *
 * <p>That choice also handles cleanup for free: a {@link WeakHashMap} lets a closed tab's
 * entry be collected once Burp drops the component, so nothing accumulates over a long
 * session and no close-listener is needed.
 *
 * <p>A null key (the tab couldn't be identified — see {@link RepeaterTabTitler#activeTabKey()})
 * is stored under a single shared bucket rather than dropped, so the feature still works in
 * that degraded case.
 */
public final class TabNamePartStore {

    /** Slots the user can fill, addressed as 1..PART_COUNT. */
    public static final int PART_COUNT = 3;

    // WeakHashMap is not thread-safe and its entries can disappear between calls, so every
    // access is synchronized on the map itself. Writes and reads both happen on the EDT today;
    // the guard keeps that from being a silent assumption.
    private final Map<Object, String[]> partsByTab = Collections.synchronizedMap(new WeakHashMap<>());

    /** Key stand-in for "couldn't identify the tab"; WeakHashMap tolerates null keys directly. */
    private static final Object UNSCOPED = new Object();

    /**
     * @param slot  1-based slot index
     * @param value the raw selected text, or null/blank to clear that slot
     */
    public void setPart(Object tabKey, int slot, String value) {
        if (slot < 1 || slot > PART_COUNT) {
            return;
        }
        Object key = keyFor(tabKey);
        synchronized (partsByTab) {
            String[] parts = partsByTab.computeIfAbsent(key, k -> new String[PART_COUNT]);
            parts[slot - 1] = (value == null || value.isBlank()) ? null : value;
            if (Arrays.stream(parts).allMatch(Objects::isNull)) {
                partsByTab.remove(key);
            }
        }
    }

    /** The filled slots for a tab, in slot order, with gaps skipped. Never null. */
    public List<String> partsFor(Object tabKey) {
        synchronized (partsByTab) {
            String[] parts = partsByTab.get(keyFor(tabKey));
            if (parts == null) {
                return List.of();
            }
            List<String> filled = new ArrayList<>(PART_COUNT);
            for (String part : parts) {
                if (part != null && !part.isBlank()) {
                    filled.add(part);
                }
            }
            return filled;
        }
    }

    public void clear(Object tabKey) {
        partsByTab.remove(keyFor(tabKey));
    }

    /** Drops every staged part; called when the extension unloads. */
    public void clearAll() {
        partsByTab.clear();
    }

    private Object keyFor(Object tabKey) {
        return tabKey == null ? UNSCOPED : tabKey;
    }
}
