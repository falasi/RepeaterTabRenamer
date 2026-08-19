package burp.tabrenamer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TabNamePartStoreTest {

    private final TabNamePartStore store = new TabNamePartStore();
    private final Object tabA = new Object();
    private final Object tabB = new Object();

    @Test
    void partsComeBackInSlotOrderRegardlessOfCaptureOrder() {
        store.setPart(tabA, 3, "admin");
        store.setPart(tabA, 1, "POST");
        store.setPart(tabA, 2, "users");
        assertEquals(List.of("POST", "users", "admin"), store.partsFor(tabA));
    }

    @Test
    void gapsBetweenFilledSlotsAreSkipped() {
        store.setPart(tabA, 1, "POST");
        store.setPart(tabA, 3, "admin");
        assertEquals(List.of("POST", "admin"), store.partsFor(tabA));
    }

    @Test
    void reusingASlotReplacesItRatherThanAppending() {
        store.setPart(tabA, 1, "GET");
        store.setPart(tabA, 1, "POST");
        assertEquals(List.of("POST"), store.partsFor(tabA));
    }

    @Test
    void blankValueClearsOnlyThatSlot() {
        store.setPart(tabA, 1, "POST");
        store.setPart(tabA, 2, "users");
        store.setPart(tabA, 2, "   ");
        assertEquals(List.of("POST"), store.partsFor(tabA));
    }

    @Test
    void partsAreScopedPerTab() {
        store.setPart(tabA, 1, "POST");
        store.setPart(tabB, 1, "GET");
        assertEquals(List.of("POST"), store.partsFor(tabA));
        assertEquals(List.of("GET"), store.partsFor(tabB));
    }

    @Test
    void clearingOneTabLeavesOthersAlone() {
        store.setPart(tabA, 1, "POST");
        store.setPart(tabB, 1, "GET");
        store.clear(tabA);
        assertTrue(store.partsFor(tabA).isEmpty());
        assertEquals(List.of("GET"), store.partsFor(tabB));
    }

    @Test
    void unknownTabHasNoParts() {
        assertTrue(store.partsFor(new Object()).isEmpty());
    }

    @Test
    void unidentifiedTabsShareOneBucketRatherThanLosingParts() {
        store.setPart(null, 1, "POST");
        assertEquals(List.of("POST"), store.partsFor(null));
        assertTrue(store.partsFor(tabA).isEmpty());
    }

    @Test
    void slotsOutsideTheSupportedRangeAreIgnored() {
        store.setPart(tabA, 0, "nope");
        store.setPart(tabA, TabNamePartStore.PART_COUNT + 1, "nope");
        assertTrue(store.partsFor(tabA).isEmpty());
    }
}
