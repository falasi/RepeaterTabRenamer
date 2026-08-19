package burp.tabrenamer;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * Walks a chain of candidate combos and keeps the first one Burp actually accepts.
 *
 * <p>The subtlety this exists for: Burp reports a clash by <em>logging</em> "Unable to register
 * hotkey 'X' as already assigned" and handing back a {@code Registration} whose
 * {@code isRegistered()} is false — it does not throw. Registration code that only watched for
 * exceptions therefore believed it had succeeded, never tried its fallback, and advertised a
 * combo that did nothing. The return value is the authority here; exceptions are still caught,
 * but only as a secondary signal.
 *
 * <p>Montoya offers no way to ask whether a combo is free before claiming it, so this stays a
 * try-then-fall-back loop rather than a lookup.
 */
final class HotKeyRegistrar {

    enum Status {
        /** Burp accepted one of the candidates. */
        REGISTERED,
        /** This Burp is too old to have the hotkey API at all. */
        UNSUPPORTED,
        /** The API exists but every candidate was refused. */
        FAILED
    }

    record Outcome(Status status, String combo) {

        boolean isRegistered() {
            return status == Status.REGISTERED;
        }
    }

    /**
     * Attempts one combo.
     *
     * @return whether Burp accepted it — i.e. {@code Registration.isRegistered()}
     * @throws NoSuchMethodError   if this Burp predates the hotkey API
     * @throws NoClassDefFoundError if the hotkey classes are absent
     */
    interface Binder {
        boolean bind(String combo) throws Throwable;
    }

    private HotKeyRegistrar() {
    }

    /**
     * @param onRejected notified for each candidate Burp turned down, with the throwable if one
     *                   was raised and null if it simply came back unregistered
     */
    static Outcome register(List<String> candidates, Binder binder, BiConsumer<String, Throwable> onRejected) {
        for (String combo : candidates) {
            try {
                if (binder.bind(combo)) {
                    return new Outcome(Status.REGISTERED, combo);
                }
                onRejected.accept(combo, null);
            } catch (NoSuchMethodError | NoClassDefFoundError e) {
                // The whole API is missing; trying the remaining combos would fail identically.
                return new Outcome(Status.UNSUPPORTED, null);
            } catch (Throwable t) {
                onRejected.accept(combo, t);
            }
        }
        return new Outcome(Status.FAILED, null);
    }
}
