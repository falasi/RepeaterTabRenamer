package burp.tabrenamer;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HotKeyRegistrarTest {

    private final List<String> attempted = new ArrayList<>();
    private final List<String> rejected = new ArrayList<>();

    /** Accepts anything not in {@code taken}, the way Burp does: by returning false, not throwing. */
    private HotKeyRegistrar.Binder burpWith(Set<String> taken) {
        return combo -> {
            attempted.add(combo);
            return !taken.contains(combo);
        };
    }

    private HotKeyRegistrar.Outcome register(HotKeyRegistrar.Binder binder) {
        return HotKeyRegistrar.register(
                List.of("Preferred", "Fallback1", "Fallback2"),
                binder,
                (combo, error) -> rejected.add(combo));
    }

    @Test
    void keepsThePreferredComboWhenItIsFree() {
        HotKeyRegistrar.Outcome outcome = register(burpWith(Set.of()));
        assertEquals(HotKeyRegistrar.Status.REGISTERED, outcome.status());
        assertEquals("Preferred", outcome.combo());
        assertEquals(List.of("Preferred"), attempted, "must stop at the first success");
        assertTrue(rejected.isEmpty());
    }

    @Test
    void fallsBackToTheFirstAlternativeWhenThePreferredComboIsTaken() {
        HotKeyRegistrar.Outcome outcome = register(burpWith(Set.of("Preferred")));
        assertEquals(HotKeyRegistrar.Status.REGISTERED, outcome.status());
        assertEquals("Fallback1", outcome.combo());
        assertEquals(List.of("Preferred", "Fallback1"), attempted);
        assertEquals(List.of("Preferred"), rejected);
    }

    @Test
    void fallsBackFurtherWhenSeveralCombosAreTaken() {
        HotKeyRegistrar.Outcome outcome = register(burpWith(Set.of("Preferred", "Fallback1")));
        assertEquals(HotKeyRegistrar.Status.REGISTERED, outcome.status());
        assertEquals("Fallback2", outcome.combo());
        assertEquals(List.of("Preferred", "Fallback1", "Fallback2"), attempted);
    }

    @Test
    void reportsFailureWithNoComboWhenEveryCandidateIsTaken() {
        HotKeyRegistrar.Outcome outcome = register(burpWith(Set.of("Preferred", "Fallback1", "Fallback2")));
        assertEquals(HotKeyRegistrar.Status.FAILED, outcome.status());
        assertNull(outcome.combo(), "no combo may be advertised when none registered");
        assertEquals(3, attempted.size(), "every candidate must be tried before giving up");
    }

    @Test
    void treatsAnUnregisteredRegistrationAsARejection() {
        // The bug this class exists for: Burp logs "already assigned" and returns a Registration
        // whose isRegistered() is false, without throwing. An exception-only check would have
        // kept "Preferred" and advertised a combo that never fires.
        HotKeyRegistrar.Outcome outcome = register(combo -> {
            attempted.add(combo);
            return !combo.equals("Preferred");
        });
        assertEquals("Fallback1", outcome.combo());
    }

    @Test
    void alsoFallsBackWhenBurpThrowsInsteadOfReturningFalse() {
        HotKeyRegistrar.Outcome outcome = register(combo -> {
            attempted.add(combo);
            if (combo.equals("Preferred")) {
                throw new IllegalArgumentException("already assigned");
            }
            return true;
        });
        assertEquals(HotKeyRegistrar.Status.REGISTERED, outcome.status());
        assertEquals("Fallback1", outcome.combo());
        assertEquals(List.of("Preferred"), rejected);
    }

    @Test
    void stopsImmediatelyWhenTheHotkeyApiIsMissing() {
        HotKeyRegistrar.Outcome outcome = register(combo -> {
            attempted.add(combo);
            throw new NoSuchMethodError("registerHotKeyHandler");
        });
        assertEquals(HotKeyRegistrar.Status.UNSUPPORTED, outcome.status());
        assertNull(outcome.combo());
        assertEquals(List.of("Preferred"), attempted, "no point retrying combos on a Burp without the API");
    }

    @Test
    void everyPlannedHotkeyHasFallbacksAndNoDuplicateCandidates() {
        List<List<String>> chains = new ArrayList<>(List.of(HotKeyPlan.RENAME, HotKeyPlan.SEND));
        for (int slot = 1; slot <= TabNamePartStore.PART_COUNT; slot++) {
            chains.add(HotKeyPlan.part(slot));
        }
        for (List<String> chain : chains) {
            assertTrue(chain.size() >= 2, "every hotkey needs at least one fallback: " + chain);
            assertEquals(chain.size(), Set.copyOf(chain).size(), "duplicate candidate in " + chain);
        }
    }

    @Test
    void sendNoLongerDefaultsToTheComboHackvertorClaims() {
        assertEquals("Ctrl+Alt+Shift+R", HotKeyPlan.SEND.get(0));
        assertFalse(HotKeyPlan.SEND.contains("Ctrl+Alt+S"));
    }

    @Test
    void preferredCombosDoNotCollideWithEachOther() {
        List<String> preferred = new ArrayList<>(List.of(HotKeyPlan.RENAME.get(0), HotKeyPlan.SEND.get(0)));
        for (int slot = 1; slot <= TabNamePartStore.PART_COUNT; slot++) {
            preferred.add(HotKeyPlan.part(slot).get(0));
        }
        assertEquals(preferred.size(), Set.copyOf(preferred).size(),
                "this extension must not fight itself for combos: " + preferred);
    }
}
