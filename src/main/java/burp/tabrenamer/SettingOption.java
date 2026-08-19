package burp.tabrenamer;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Resolves the string Burp hands back for a list setting into one of the options offered.
 *
 * <p>Shared by every settings-backed enum so they can't drift apart in how forgiving they are.
 * Matching is deliberately lenient — exact, then case-insensitive, then containment — because
 * the option label doubles as the persisted value, so anything that ever shipped with a
 * different label (an example appended, different capitalisation) still resolves instead of
 * silently reverting a user's choice on upgrade.
 *
 * <p>An unrecognised value returns empty rather than a default, so the caller can tell the
 * difference between "the user picked nothing yet" and "this value means nothing to us" and
 * report only the latter.
 */
final class SettingOption {

    private SettingOption() {
    }

    static <T> Optional<T> resolve(T[] options, Function<T, String> label, String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return Stream.of(options)
                .filter(option -> {
                    String candidate = label.apply(option).toLowerCase(Locale.ROOT);
                    return normalized.equals(candidate) || normalized.contains(candidate);
                })
                .findFirst();
    }
}
