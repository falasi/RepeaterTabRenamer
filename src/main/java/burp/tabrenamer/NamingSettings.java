package burp.tabrenamer;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.logging.Logging;
import burp.api.montoya.ui.settings.SettingsPanelBuilder;
import burp.api.montoya.ui.settings.SettingsPanelPersistence;
import burp.api.montoya.ui.settings.SettingsPanelSetting;
import burp.api.montoya.ui.settings.SettingsPanelWithData;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The extension's user-facing preferences, surfaced in Burp's own <b>Settings → Extensions →
 * Repeater Tab Renamer</b> panel rather than a custom suite tab: it's where users already look
 * for extension settings, it's searchable from the settings filter box, and
 * {@link SettingsPanelPersistence#USER_SETTINGS} persists the choice across restarts and
 * projects without this extension writing any storage code of its own.
 *
 * <p>Values are read live on every use rather than cached, so changing the separator takes
 * effect on the next request with no reload. Reads are cheap (an in-memory lookup in Burp).
 *
 * <p>The settings-panel API postdates the oldest Burp this extension supports, so registration
 * is wrapped the same way hotkey registration is: on an older release the panel simply doesn't
 * appear and the separator stays at its default.
 */
public final class NamingSettings {

    static final String SEPARATOR_SETTING = "Join generated name parts with";
    private static final String SEPARATOR_DESCRIPTION =
            "Used between the pieces of an automatically generated name, before a duplicate's "
                    + "number, and between multiple saved selections.";

    private final SettingsPanelWithData panel;
    private final Logging logging;
    private final AtomicBoolean readFailureLogged = new AtomicBoolean();

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
                            NameSeparator.HYPHEN.label()))
                    .build();
            api.userInterface().registerSettingsPanel(panel);
            return new NamingSettings(panel, api.logging());
        } catch (Throwable t) {
            api.logging().logToOutput("repeater-tab-renamer: this Burp version has no extension settings panel; "
                    + "names will be joined with \"" + NameSeparator.HYPHEN.value() + "\".");
            return new NamingSettings(null, api.logging());
        }
    }

    public NameSeparator separator() {
        if (panel == null) {
            return NameSeparator.HYPHEN;
        }
        try {
            return NameSeparator.fromLabel(panel.getString(SEPARATOR_SETTING));
        } catch (Throwable t) {
            // Logged once rather than per request: this is read on every generated name, and a
            // broken settings panel would otherwise flood the Errors tab.
            if (readFailureLogged.compareAndSet(false, true)) {
                logging.logToError("repeater-tab-renamer: could not read the separator setting; "
                        + "falling back to \"" + NameSeparator.HYPHEN.value() + "\"", t);
            }
            return NameSeparator.HYPHEN;
        }
    }
}
