package burp.tabrenamer;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.Registration;
import burp.api.montoya.ui.hotkey.HotKey;
import burp.api.montoya.ui.hotkey.HotKeyContext;
import burp.api.montoya.ui.hotkey.HotKeyHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Auto-renames Repeater tabs based on the request they hold: the last path segment for
 * GET-style requests (and for POST/PUT/etc. whose path is already distinctive), falling back
 * to a meaningful field from the request body when the path itself is too generic to tell
 * requests apart (e.g. a single "/graphql" or "/api" endpoint). A name already in use gets a
 * "(2)", "(3)" suffix so tabs stay distinguishable.
 *
 * <p>Tabs can also be named explicitly, via hotkeys (each independently rebindable via Burp's
 * Settings → Hotkeys) that between them cover every place selected text can come from:
 * <ul>
 *   <li><b>Rename</b> — fires in any focused message editor pane. In Repeater, renames the
 *       active tab from the staged parts if there are any, otherwise from the selection.
 *       Anywhere else (Proxy history's viewer, Intruder's, ...), sends the message to a new,
 *       already-named Repeater tab instead. Also reachable in Repeater via right-click.</li>
 *   <li><b>Stage part 1/2/3</b> — stage the current Repeater selection as that numbered piece
 *       of the next name, so separate selections can be combined ("POST" + "users" + "admin" →
 *       "POST-users-admin"). Pressing one with nothing selected clears that piece.</li>
 *   <li><b>Send</b> — fires when a Proxy history row is selected but its editor pane isn't
 *       focused (clicking a row keeps focus on the table, not the viewer). Sends that request to
 *       a new, already-named Repeater tab.</li>
 * </ul>
 * The split between the editor and history hotkeys exists because Burp scopes hotkeys by
 * whichever specific component has focus, not by tool/tab: a table and its own embedded editor
 * pane report as different hotkey contexts even though they're part of the same view, so one
 * hotkey can't cover both. Default combos and their fallbacks live in {@link HotKeyPlan}.
 *
 * <p>How the pieces of a generated name are joined is a user preference — see
 * {@link NamingSettings}. All dependence on Burp's internal Swing structure is confined to
 * {@link RepeaterUiLocator}.
 */
public class RepeaterTabRenamerExtension implements BurpExtension {

    private static final String RENAME_HOTKEY_NAME = "Use selection as Repeater tab name";
    private static final String SEND_HOTKEY_NAME = "Send to Repeater (named from selection)";

    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName("Repeater Tab Renamer");

        NamingSettings settings = NamingSettings.register(api);
        TabNameGenerator nameGenerator = new TabNameGenerator(settings::config);
        RepeaterUiLocator locator = new RepeaterUiLocator(
                api.userInterface().swingUtils().suiteFrame(), api.logging());
        RepeaterTabTitler tabTitler = new RepeaterTabTitler(locator, nameGenerator, api.logging());
        TabNamePartStore partStore = new TabNamePartStore();
        SendToRepeaterHotKeyHandler sendHandler =
                new SendToRepeaterHotKeyHandler(nameGenerator, tabTitler, api.repeater(), api.logging());

        api.http().registerHttpHandler(new RepeaterRequestHandler(nameGenerator, tabTitler, api.logging()));
        api.userInterface().registerContextMenuItemsProvider(
                new RepeaterSelectionContextMenuProvider(nameGenerator, tabTitler, partStore, api.logging()));

        // Registered independently — one hotkey exhausting its candidates must not stop the
        // others from registering, and each result is reported on its own.
        HotKeyRegistrar.Outcome rename = registerHotKey(api, HotKeyContext.HTTP_MESSAGE_EDITOR,
                RENAME_HOTKEY_NAME, HotKeyPlan.RENAME,
                new MessageEditorSelectionHotKeyHandler(nameGenerator, tabTitler, partStore, sendHandler, api.logging()));

        List<HotKeyRegistrar.Outcome> parts = new ArrayList<>();
        for (int slot = 1; slot <= TabNamePartStore.PART_COUNT; slot++) {
            parts.add(registerHotKey(api, HotKeyContext.HTTP_MESSAGE_EDITOR,
                    "Set Repeater tab name part " + slot, HotKeyPlan.part(slot),
                    new SelectionPartHotKeyHandler(slot, partStore, tabTitler, api.logging())));
        }

        HotKeyRegistrar.Outcome send = registerHotKey(api, HotKeyContext.PROXY_HTTP_HISTORY,
                SEND_HOTKEY_NAME, HotKeyPlan.SEND, sendHandler);

        // Burp deregisters our handlers itself; what it can't know about is the state we hold,
        // so both caches are dropped explicitly rather than left to reload-time garbage.
        api.extension().registerUnloadingHandler(() -> {
            partStore.clearAll();
            locator.forget();
            api.logging().logToOutput("Repeater Tab Renamer unloaded.");
        });

        api.logging().logToOutput(UsageBanner.render(version(), UsageBanner.rows(rename, parts, send)));
    }

    /**
     * Claims the first combo from {@code candidates} that Burp accepts.
     *
     * <p>Acceptance is decided by {@link Registration#isRegistered()}, not by the absence of an
     * exception: Burp signals "already assigned" by logging it and returning an unregistered
     * {@code Registration}, so an exception-only check silently keeps a combo that never fires.
     */
    private HotKeyRegistrar.Outcome registerHotKey(MontoyaApi api, HotKeyContext context, String name,
                                                   List<String> candidates, HotKeyHandler handler) {
        return HotKeyRegistrar.register(
                candidates,
                combo -> {
                    Registration registration =
                            api.userInterface().registerHotKeyHandler(context, HotKey.hotKey(name, combo), handler);
                    return registration != null && registration.isRegistered();
                },
                (combo, error) -> {
                    if (error != null) {
                        api.logging().logToError("repeater-tab-renamer: hotkey \"" + name + "\" combo "
                                + combo + " was refused", error);
                    }
                    // A plain refusal needs no log line: Burp already prints its own "already
                    // assigned" message, and the banner reports whichever combo finally won.
                });
    }

    /**
     * The version from the jar manifest, or null when it isn't available (running from compiled
     * classes, or a classloader that doesn't expose package metadata). Omitted rather than
     * guessed — a wrong version number in a bug report is worse than none.
     */
    private String version() {
        try {
            return getClass().getPackage().getImplementationVersion();
        } catch (Exception e) {
            return null;
        }
    }
}
