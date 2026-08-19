package burp.tabrenamer;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
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
 *   <li><b>Ctrl+Alt+R</b> — fires in any focused message editor pane. In Repeater, renames the
 *       active tab from the staged parts if there are any, otherwise from the selection.
 *       Anywhere else (Proxy history's viewer, Intruder's, ...), sends the message to a new,
 *       already-named Repeater tab instead. Also reachable in Repeater via right-click.</li>
 *   <li><b>Ctrl+Alt+1/2/3</b> — stage the current Repeater selection as that numbered piece of
 *       the next name, so separate selections can be combined ("POST" + "users" + "admin" →
 *       "POST-users-admin"). Pressing one with nothing selected clears that piece.</li>
 *   <li><b>Ctrl+Alt+S</b> — fires when a Proxy history row is selected but its editor pane isn't
 *       focused (clicking a row keeps focus on the table, not the viewer). Sends that request to
 *       a new, already-named Repeater tab.</li>
 * </ul>
 * Each falls back to a Ctrl+Shift+ variant if its first choice is already taken. The split
 * between the editor and history hotkeys exists because Burp scopes hotkeys by whichever
 * specific component has focus, not by tool/tab: a table and its own embedded editor pane
 * report as different hotkey contexts even though they're part of the same view, so one hotkey
 * can't cover both.
 *
 * <p>How the pieces of a generated name are joined is a user preference — see
 * {@link NamingSettings}. All dependence on Burp's internal Swing structure is confined to
 * {@link RepeaterUiLocator}.
 */
public class RepeaterTabRenamerExtension implements BurpExtension {

    private static final String RENAME_HOTKEY_NAME = "Use selection as Repeater tab name";
    private static final String[] RENAME_HOTKEY_COMBOS = {"Ctrl+Alt+R", "Ctrl+Shift+R"};
    private static final String RENAME_HOTKEY_USAGE =
            "Rename the current Repeater tab from the selected text,\n"
                    + "or from the staged parts if any.\n"
                    + "In other tools: send the message to a new, named Repeater tab.";

    private static final String SEND_HOTKEY_NAME = "Send to Repeater (named from selection)";
    private static final String[] SEND_HOTKEY_COMBOS = {"Ctrl+Alt+S", "Ctrl+Shift+S"};
    private static final String SEND_HOTKEY_USAGE =
            "Send to Repeater with automatic naming, for a Proxy history row selected without\n"
                    + "clicking into its viewer pane.";

    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName("Repeater Tab Renamer");

        NamingSettings settings = NamingSettings.register(api);
        TabNameGenerator nameGenerator = new TabNameGenerator(settings::separator);
        RepeaterUiLocator locator = new RepeaterUiLocator(
                api.userInterface().swingUtils().suiteFrame(), api.logging());
        RepeaterTabTitler tabTitler = new RepeaterTabTitler(locator, nameGenerator, api.logging());
        TabNamePartStore partStore = new TabNamePartStore();
        SendToRepeaterHotKeyHandler sendHandler =
                new SendToRepeaterHotKeyHandler(nameGenerator, tabTitler, api.repeater(), api.logging());

        api.http().registerHttpHandler(new RepeaterRequestHandler(nameGenerator, tabTitler, api.logging()));
        api.userInterface().registerContextMenuItemsProvider(
                new RepeaterSelectionContextMenuProvider(nameGenerator, tabTitler, partStore, api.logging()));

        // Registered independently — a failure registering one (e.g. every one of its candidate
        // combos already taken by another extension) must not stop the others from registering,
        // and each result is reported on its own rather than lumped into a single pass/fail.
        List<HotKeyStatus> statuses = new ArrayList<>();
        statuses.add(new HotKeyStatus(RENAME_HOTKEY_NAME, RENAME_HOTKEY_USAGE,
                registerHotKey(api, HotKeyContext.HTTP_MESSAGE_EDITOR, RENAME_HOTKEY_NAME, RENAME_HOTKEY_COMBOS,
                        new MessageEditorSelectionHotKeyHandler(nameGenerator, tabTitler, partStore, sendHandler, api.logging()))));

        for (int slot = 1; slot <= TabNamePartStore.PART_COUNT; slot++) {
            String name = "Set Repeater tab name part " + slot;
            // Ctrl+Alt+<digit> is unclaimed in stock Burp (its own shortcuts are letter-based),
            // and keeps the whole feature on one modifier pair with the rename key it feeds.
            String[] combos = {"Ctrl+Alt+" + slot, "Ctrl+Shift+" + slot};
            String usage = "Store the Repeater selection as name part " + slot
                    + (slot == 1 ? " (press with nothing selected to clear it)" : "");
            statuses.add(new HotKeyStatus(name, usage,
                    registerHotKey(api, HotKeyContext.HTTP_MESSAGE_EDITOR, name, combos,
                            new SelectionPartHotKeyHandler(slot, partStore, tabTitler, api.logging()))));
        }

        statuses.add(new HotKeyStatus(SEND_HOTKEY_NAME, SEND_HOTKEY_USAGE,
                registerHotKey(api, HotKeyContext.PROXY_HTTP_HISTORY, SEND_HOTKEY_NAME, SEND_HOTKEY_COMBOS,
                        sendHandler)));

        // Burp deregisters our handlers itself; what it can't know about is the state we hold,
        // so both caches are dropped explicitly rather than left to reload-time garbage.
        api.extension().registerUnloadingHandler(() -> {
            partStore.clearAll();
            locator.forget();
            api.logging().logToOutput("Repeater Tab Renamer unloaded.");
        });

        api.logging().logToOutput(usageBanner(statuses));
    }

    private enum HotKeyResult {
        /** Registered successfully — with its first-choice combo, or a fallback if that one was taken. */
        REGISTERED,
        /** This Burp version doesn't support hotkey registration at all (older than montoya-api 2025.11/.12). */
        UNSUPPORTED,
        /** The API is supported but every candidate combo failed to register (e.g. all already bound elsewhere). */
        FAILED
    }

    private record HotKeyRegistration(HotKeyResult result, String combo) {
    }

    private record HotKeyStatus(String name, String usage, HotKeyRegistration registration) {
    }

    /**
     * Tries each combo in {@code combos} in order, using the first one Burp accepts. A combo
     * being already bound (by this extension's own other hotkey, another extension, or the user)
     * surfaces as an exception from {@code registerHotKeyHandler} — there's no "is this combo
     * free" query to check upfront, so this is a reactive try-then-fall-back rather than a
     * proactive detection.
     */
    private HotKeyRegistration registerHotKey(MontoyaApi api, HotKeyContext context, String name, String[] combos, HotKeyHandler handler) {
        for (String combo : combos) {
            try {
                api.userInterface().registerHotKeyHandler(context, HotKey.hotKey(name, combo), handler);
                return new HotKeyRegistration(HotKeyResult.REGISTERED, combo);
            } catch (NoSuchMethodError | NoClassDefFoundError e) {
                return new HotKeyRegistration(HotKeyResult.UNSUPPORTED, null);
            } catch (Throwable t) {
                api.logging().logToError("repeater-tab-renamer: hotkey \"" + name + "\" combo " + combo + " unavailable", t);
            }
        }
        return new HotKeyRegistration(HotKeyResult.FAILED, null);
    }

    /**
     * Builds the load-time usage banner. Shortcuts are reported as actually registered, never as
     * the requested constants: a combo already claimed elsewhere falls back to its alternative,
     * and printing the first choice would tell the user to press a key that does nothing. Montoya
     * exposes no getter for a hotkey's live binding, so a combo the user rebinds after load can't
     * be reflected here — hence the pointer to Settings > Hotkeys.
     */
    private String usageBanner(List<HotKeyStatus> statuses) {
        List<UsageBanner.Shortcut> shortcuts = statuses.stream()
                .map(status -> new UsageBanner.Shortcut(comboLabel(status.registration()), status.usage()))
                .toList();
        return UsageBanner.render(
                shortcuts,
                statuses.stream().anyMatch(s -> s.registration().result() == HotKeyResult.UNSUPPORTED),
                statuses.stream().anyMatch(s -> s.registration().result() == HotKeyResult.FAILED));
    }

    private String comboLabel(HotKeyRegistration registration) {
        return switch (registration.result()) {
            case REGISTERED -> registration.combo();
            case UNSUPPORTED -> "(unavailable)";
            case FAILED -> "(unregistered)";
        };
    }
}
