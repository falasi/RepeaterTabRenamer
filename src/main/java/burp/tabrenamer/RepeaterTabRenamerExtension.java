package burp.tabrenamer;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.logging.Logging;
import burp.api.montoya.ui.hotkey.HotKey;
import burp.api.montoya.ui.hotkey.HotKeyContext;
import burp.api.montoya.ui.hotkey.HotKeyHandler;

/**
 * Auto-renames Repeater tabs based on the request they hold: the last path segment for
 * GET-style requests (and for POST/PUT/etc. whose path is already distinctive), falling back
 * to a meaningful field from the request body when the path itself is too generic to tell
 * requests apart (e.g. a single "/graphql" or "/api" endpoint).
 *
 * <p>Also lets you name a tab explicitly, via two hotkeys (each independently rebindable via
 * Burp's Settings → Hotkeys) that between them cover every place selected text can come from:
 * <ul>
 *   <li><b>Ctrl+Alt+R</b> (falls back to Ctrl+Shift+R if taken) — fires in any focused message
 *       editor pane. In Repeater, renames the active tab from the selection. Anywhere else
 *       (Proxy history's viewer, Intruder's, ...), sends the message to a new, already-named
 *       Repeater tab instead. Also reachable in Repeater via right-click → "Use selection as
 *       Repeater tab name".</li>
 *   <li><b>Ctrl+Alt+S</b> (falls back to Ctrl+Shift+S if taken) — fires when a Proxy history row
 *       is selected but its editor pane isn't focused (clicking a row keeps focus on the table,
 *       not the viewer). Sends that request to a new, already-named Repeater tab.</li>
 * </ul>
 * The split exists because Burp scopes hotkeys by whichever specific component has focus, not
 * by tool/tab: a table and its own embedded editor pane report as different hotkey contexts even
 * though they're part of the same view, so one hotkey can't cover both.
 */
public class RepeaterTabRenamerExtension implements BurpExtension {

    private static final String RENAME_HOTKEY_NAME = "Use selection as Repeater tab name";
    private static final String[] RENAME_HOTKEY_COMBOS = {"Ctrl+Alt+R", "Ctrl+Shift+R"};
    private static final String RENAME_HOTKEY_CONTEXT_DESC = "in any message editor — renames in Repeater, sends+names elsewhere";

    private static final String SEND_HOTKEY_NAME = "Send to Repeater (named from selection)";
    private static final String[] SEND_HOTKEY_COMBOS = {"Ctrl+Alt+S", "Ctrl+Shift+S"};
    private static final String SEND_HOTKEY_CONTEXT_DESC = "for a selected Proxy history row (editor pane not focused)";

    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName("Repeater Tab Renamer");

        TabNameGenerator nameGenerator = new TabNameGenerator();
        RepeaterTabTitler tabTitler = new RepeaterTabTitler(api.userInterface().swingUtils().suiteFrame(), api.logging());
        SendToRepeaterHotKeyHandler sendHandler = new SendToRepeaterHotKeyHandler(nameGenerator, api.repeater(), api.logging());

        api.http().registerHttpHandler(new RepeaterRequestHandler(nameGenerator, tabTitler, api.logging()));
        api.userInterface().registerContextMenuItemsProvider(
                new RepeaterSelectionContextMenuProvider(nameGenerator, tabTitler, api.logging()));

        // Registered independently — a failure registering one (e.g. every one of its candidate
        // combos already taken by another extension) must not stop the other from registering,
        // and each result is reported on its own rather than lumped into a single pass/fail.
        HotKeyRegistration renameReg = registerHotKey(api, HotKeyContext.HTTP_MESSAGE_EDITOR,
                RENAME_HOTKEY_NAME, RENAME_HOTKEY_COMBOS,
                new MessageEditorSelectionHotKeyHandler(nameGenerator, tabTitler, sendHandler, api.logging()));
        HotKeyRegistration sendReg = registerHotKey(api, HotKeyContext.PROXY_HTTP_HISTORY,
                SEND_HOTKEY_NAME, SEND_HOTKEY_COMBOS,
                sendHandler);

        api.extension().registerUnloadingHandler(() -> api.logging().logToOutput("Repeater Tab Renamer unloaded."));

        api.logging().logToOutput("Repeater Tab Renamer loaded.");
        logHotKeyStatus(api.logging(), renameReg, sendReg);
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
            } catch (NoSuchMethodError e) {
                return new HotKeyRegistration(HotKeyResult.UNSUPPORTED, null);
            } catch (Throwable t) {
                api.logging().logToError("repeater-tab-renamer: hotkey \"" + name + "\" combo " + combo + " unavailable", t);
            }
        }
        return new HotKeyRegistration(HotKeyResult.FAILED, null);
    }

    /**
     * Montoya's {@code HotKey}/{@code Registration} types don't expose a getter for a hotkey's
     * current live binding — only whichever combo we successfully requested at registration. So
     * a successful registration can only ever be reported with that combo, with an explicit note
     * that it may have been rebound since; claiming to show the "current" binding would be a
     * guess dressed up as fact.
     */
    private void logHotKeyStatus(Logging logging, HotKeyRegistration renameReg, HotKeyRegistration sendReg) {
        StringBuilder message = new StringBuilder("Hotkeys (bindings below reflect what registered just now — check Settings > Hotkeys if you've rebound one since):\n");
        message.append(statusLine(RENAME_HOTKEY_NAME, RENAME_HOTKEY_CONTEXT_DESC, renameReg)).append('\n');
        message.append(statusLine(SEND_HOTKEY_NAME, SEND_HOTKEY_CONTEXT_DESC, sendReg));

        if (renameReg.result() == HotKeyResult.UNSUPPORTED || sendReg.result() == HotKeyResult.UNSUPPORTED) {
            message.append("\nHotkeys need a Burp release supporting montoya-api 2025.12+; the right-click "
                    + "\"" + RENAME_HOTKEY_NAME + "\" menu item still works regardless.");
        }
        if (renameReg.result() == HotKeyResult.FAILED || sendReg.result() == HotKeyResult.FAILED) {
            message.append("\nA hotkey failed to register — every candidate combo is already bound to "
                    + "something else. See the Errors log for details, or bind it manually via Settings > Hotkeys.");
        }

        logging.logToOutput(message.toString());
    }

    private String statusLine(String name, String contextDesc, HotKeyRegistration reg) {
        return switch (reg.result()) {
            case REGISTERED -> "  " + reg.combo() + "  \"" + name + "\" (" + contextDesc + ")";
            case UNSUPPORTED -> "  (unavailable on this Burp version)  \"" + name + "\"";
            case FAILED -> "  (failed to register — see Errors)  \"" + name + "\"";
        };
    }
}
