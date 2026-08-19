package burp.tabrenamer;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.logging.Logging;
import burp.api.montoya.ui.settings.SettingsPanelBuilder;
import burp.api.montoya.ui.settings.SettingsPanelPersistence;
import burp.api.montoya.ui.settings.SettingsPanelSetting;
import burp.api.montoya.ui.settings.SettingsPanelWithData;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * The extension's preferences, surfaced in Burp's own <b>Settings → Extensions → Repeater Tab
 * Renamer</b> panel rather than a custom suite tab: it's where users already look for extension
 * settings, it's searchable from the settings filter box, and
 * {@link SettingsPanelPersistence#USER_SETTINGS} persists choices across restarts and projects
 * without this extension writing any storage code of its own.
 *
 * <p><b>Argument order matters here.</b> {@code listSetting}'s four-argument overload is
 * {@code (description, name, values, defaultValue)} — description first, mirroring
 * {@code stringSetting} and {@code integerSetting}. Passing name first instead registers the
 * setting under the description text, which both renders that long text as the control's label
 * (pushing the control off-screen) and makes {@code getString(name)} look up something that was
 * never registered, so it reads back as nothing no matter what the user picks. That was the
 * cause of the separator preference appearing to do nothing.
 *
 * <p>Descriptions are kept to one short line for the same layout reason.
 *
 * <p>Values are read live on every use rather than cached, so a change takes effect on the very
 * next name with no reload. Reads are an in-memory lookup in Burp.
 */
public final class NamingSettings {

    // Once shipped, these names are the persistence keys. Renaming one silently discards every
    // user's saved choice, so they stay put.
    static final String SEPARATOR_SETTING = "Name separator";
    static final String FORMAT_SETTING = "Automatic naming format";

    private static final String SEPARATOR_DESCRIPTION = "Separator used between generated name parts.";
    private static final String FORMAT_DESCRIPTION = "How tabs are named automatically.";

    private final SettingsPanelWithData panel;
    private final Logging logging;
    private final AtomicBoolean readFailureLogged = new AtomicBoolean();
    private final AtomicBoolean unknownValueLogged = new AtomicBoolean();

    private NamingSettings(SettingsPanelWithData panel, Logging logging) {
        this.panel = panel;
        this.logging = logging;
    }

    /** Never throws: a Burp too old to have the settings API yields a defaults-only instance. */
    public static NamingSettings register(MontoyaApi api) {
        try {
            SettingsPanelWithData panel = SettingsPanelBuilder.settingsPanel()
                    .withPersistence(SettingsPanelPersistence.USER_SETTINGS)
                    .withTitle("Repeater Tab Renamer")
                    .withKeywords("repeater", "tab", "rename", "name", "separator", "format")
                    .withSettings(
                            SettingsPanelSetting.listSetting(
                                    FORMAT_DESCRIPTION, FORMAT_SETTING,
                                    NamingFormat.labels(), NamingConfig.DEFAULT.format().label()),
                            SettingsPanelSetting.listSetting(
                                    SEPARATOR_DESCRIPTION, SEPARATOR_SETTING,
                                    NameSeparator.labels(), NamingConfig.DEFAULT.separator().label()))
                    .build();
            api.userInterface().registerSettingsPanel(panel);
            return new NamingSettings(panel, api.logging());
        } catch (Throwable t) {
            api.logging().logToOutput("repeater-tab-renamer: this Burp version has no extension settings "
                    + "panel; using the default naming format and separator.");
            return new NamingSettings(null, api.logging());
        }
    }

    /** The current preferences. Never null, never throws, safe from any thread. */
    public NamingConfig config() {
        if (panel == null) {
            return NamingConfig.DEFAULT;
        }
        return new NamingConfig(
                read(SEPARATOR_SETTING, NameSeparator::fromSetting, NamingConfig.DEFAULT.separator()),
                read(FORMAT_SETTING, NamingFormat::fromSetting, NamingConfig.DEFAULT.format()));
    }

    /**
     * Reads one setting, treating "nothing stored yet" as a silent use of the default.
     *
     * <p>Burp returns nothing for a setting the user has never touched — the value declared at
     * registration seeds the control, not the stored data — so a fresh install and an upgrade
     * that adds a setting both land here. That is the normal path, not a fault, and it must not
     * produce a warning telling the user to go and re-pick something.
     *
     * <p>A value that is present but unrecognised is different: something is genuinely wrong, so
     * it's reported once. Adding an option (Pipe, say) never lands here, because existing stored
     * values still match themselves.
     *
     * <p>Correcting the stored value in place isn't possible: {@code SettingData} exposes only
     * getters, so there is no supported way to write a setting back from an extension. Falling
     * back per read is equivalent in effect — the user is never asked to fix it by hand.
     */
    private <T> T read(String name, Function<String, Optional<T>> resolver, T fallback) {
        String raw;
        try {
            raw = panel.getString(name);
        } catch (Throwable t) {
            // Logged once rather than per request: this is read for every generated name, and a
            // broken settings panel would otherwise flood the Errors tab.
            if (readFailureLogged.compareAndSet(false, true)) {
                logging.logToError("repeater-tab-renamer: could not read the \"" + name
                        + "\" setting; using its default", t);
            }
            return fallback;
        }

        if (raw == null || raw.isBlank()) {
            return fallback;
        }

        Optional<T> resolved = resolver.apply(raw);
        if (resolved.isEmpty() && unknownValueLogged.compareAndSet(false, true)) {
            logging.logToError("repeater-tab-renamer: the \"" + name + "\" setting holds an unrecognised "
                    + "value \"" + raw + "\"; using " + fallback + " instead.");
        }
        return resolved.orElse(fallback);
    }
}
