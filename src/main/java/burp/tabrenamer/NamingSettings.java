package burp.tabrenamer;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.logging.Logging;
import burp.api.montoya.ui.settings.SettingsPanelBuilder;
import burp.api.montoya.ui.settings.SettingsPanelPersistence;
import burp.api.montoya.ui.settings.SettingsPanelSetting;
import burp.api.montoya.ui.settings.SettingsPanelWithData;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The extension's user-facing preferences, surfaced in Burp's own <b>Settings → Extensions →
 * Repeater Tab Renamer</b> panel rather than a custom suite tab: it's where users already look
 * for extension settings, it's searchable from the settings filter box, and
 * {@link SettingsPanelPersistence#USER_SETTINGS} persists the choice across restarts and
 * projects without this extension writing any storage code of its own.
 *
 * <p>The value is read live on every use rather than cached, so changing the separator takes
 * effect on the very next name with no reload. Reads are an in-memory lookup in Burp.
 *
 * <p>Anything unexpected here is reported rather than absorbed. A value that doesn't resolve is
 * logged once with the raw text: silently falling back to the default is what made an earlier
 * label-matching bug invisible, where the preference appeared to be ignored with nothing in the
 * logs to explain it.
 *
 * <p>The settings-panel API postdates the oldest Burp this extension supports, so registration
 * is wrapped the same way hotkey registration is: on an older release the panel simply doesn't
 * appear and the separator stays at its default.
 */
public final class NamingSettings {

    static final String SEPARATOR_SETTING = "Name separator";
    private static final String SEPARATOR_DESCRIPTION =
            "Joins the pieces of a generated name and replaces characters that aren't allowed in "
                    + "a tab name. Hyphen: api-users. Space: api users. Underscore: api_users. "
                    + "A duplicate tab's number is always \"name (2)\", whichever is chosen.";

    private static final NameSeparator DEFAULT_SEPARATOR = NameSeparator.HYPHEN;

    private final SettingsPanelWithData panel;
    private final Logging logging;
    private final AtomicBoolean readFailureLogged = new AtomicBoolean();
    private final AtomicBoolean unresolvedValueLogged = new AtomicBoolean();

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
                    .withDescription("How automatically generated Repeater tab names are put together.")
                    .withKeywords("repeater", "tab", "rename", "name", "separator")
                    .withSetting(SettingsPanelSetting.listSetting(
                            SEPARATOR_SETTING,
                            SEPARATOR_DESCRIPTION,
                            NameSeparator.labels(),
                            DEFAULT_SEPARATOR.label()))
                    .build();
            api.userInterface().registerSettingsPanel(panel);
            return new NamingSettings(panel, api.logging());
        } catch (Throwable t) {
            api.logging().logToOutput("repeater-tab-renamer: this Burp version has no extension settings panel; "
                    + "names will be joined with \"" + DEFAULT_SEPARATOR.value() + "\".");
            return new NamingSettings(null, api.logging());
        }
    }

    public NameSeparator separator() {
        if (panel == null) {
            return DEFAULT_SEPARATOR;
        }
        String raw;
        try {
            raw = panel.getString(SEPARATOR_SETTING);
        } catch (Throwable t) {
            // Logged once rather than per request: this is read for every generated name, and a
            // broken settings panel would otherwise flood the Errors tab.
            if (readFailureLogged.compareAndSet(false, true)) {
                logging.logToError("repeater-tab-renamer: could not read the \"" + SEPARATOR_SETTING
                        + "\" setting; using \"" + DEFAULT_SEPARATOR.value() + "\"", t);
            }
            return DEFAULT_SEPARATOR;
        }

        Optional<NameSeparator> resolved = NameSeparator.fromSetting(raw);
        if (resolved.isEmpty() && unresolvedValueLogged.compareAndSet(false, true)) {
            logging.logToError("repeater-tab-renamer: the \"" + SEPARATOR_SETTING + "\" setting reads \""
                    + raw + "\", which doesn't match any known option " + NameSeparator.labels()
                    + "; using \"" + DEFAULT_SEPARATOR.value() + "\". Re-pick the option in "
                    + "Settings > Extensions > Repeater Tab Renamer.");
        }
        return resolved.orElse(DEFAULT_SEPARATOR);
    }
}
