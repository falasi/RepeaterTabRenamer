package burp.tabrenamer;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.logging.Logging;
import burp.api.montoya.repeater.Repeater;
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse;
import burp.api.montoya.ui.hotkey.HotKeyEvent;
import burp.api.montoya.ui.hotkey.HotKeyHandler;

import java.util.List;
import java.util.Optional;

/**
 * Sends a request to a new, already-named Repeater tab, named from whatever text is selected in
 * the source request/response viewer — or auto-named the same way {@link RepeaterRequestHandler}
 * would if nothing is selected.
 *
 * <p>Unlike the rest of this extension, naming here needs no Swing heuristic: Montoya's own
 * {@code Repeater.sendToRepeater(request, name)} names the tab it creates directly, since we're
 * the one creating it. {@link RepeaterTabTitler} is consulted only to read the tab titles that
 * already exist, so sending three requests that all reduce to the same name produces
 * "api-users", "api-users-2", "api-users-3" instead of three identical tabs. If those titles
 * can't be read the name is used unchanged, so this can only ever fail back to the old
 * behavior.
 *
 * <p>Used two ways:
 * <ul>
 *   <li>As a standalone {@link HotKeyHandler} registered under
 *       {@code HotKeyContext.PROXY_HTTP_HISTORY} — fires when a history row is selected but no
 *       message editor is focused (e.g. you clicked a row without clicking into its viewer).</li>
 *   <li>Via {@link #sendFromEditor}, called directly by {@link MessageEditorSelectionHotKeyHandler}
 *       when the focused message editor's source isn't Repeater — covers clicking/selecting text
 *       inside a viewer pane (Proxy history's, Intruder's, ...), which reports the generic
 *       {@code HTTP_MESSAGE_EDITOR} context rather than a tool-specific one.</li>
 * </ul>
 */
public final class SendToRepeaterHotKeyHandler implements HotKeyHandler {

    private final TabNameGenerator nameGenerator;
    private final RepeaterTabTitler tabTitler;
    private final Repeater repeater;
    private final Logging logging;

    public SendToRepeaterHotKeyHandler(TabNameGenerator nameGenerator, RepeaterTabTitler tabTitler,
                                       Repeater repeater, Logging logging) {
        this.nameGenerator = nameGenerator;
        this.tabTitler = tabTitler;
        this.repeater = repeater;
        this.logging = logging;
    }

    @Override
    public void handle(HotKeyEvent event) {
        try {
            Optional<MessageEditorHttpRequestResponse> editorOpt = event.messageEditorRequestResponse();
            List<HttpRequestResponse> selected = event.selectedRequestResponses();

            Optional<Resolved> resolved = editorOpt.isPresent()
                    ? resolveFromEditor(editorOpt.get())
                    : resolveFromSelectedRows(selected);

            if (resolved.isEmpty()) {
                logging.logToOutput("repeater-tab-renamer: nothing to send — no request available from the "
                        + "focused editor or table selection. Click a row in Proxy > HTTP history (or select "
                        + "text in its request/response viewer) first.");
                return;
            }

            send(resolved.get());
        } catch (Exception e) {
            logging.logToError("repeater-tab-renamer: failed to send to Repeater from hotkey", e);
        }
    }

    /** Entry point for {@link MessageEditorSelectionHotKeyHandler}: a message editor is already
     *  known to be focused, so there's no table-row fallback to consider. */
    void sendFromEditor(MessageEditorHttpRequestResponse editor) {
        try {
            resolveFromEditor(editor).ifPresentOrElse(
                    this::send,
                    () -> logging.logToOutput("repeater-tab-renamer: nothing to send — the focused message has no request."));
        } catch (Exception e) {
            logging.logToError("repeater-tab-renamer: failed to send to Repeater from hotkey", e);
        }
    }

    private void send(Resolved resolved) {
        // Deliberately resolved here rather than at name-generation time: this is the last
        // moment before the tab exists, so the comparison is against the freshest tab list.
        // Succeeds silently: the new, named Repeater tab is the confirmation.
        repeater.sendToRepeater(resolved.request(), tabTitler.uniqueNewTabName(resolved.name()));
    }

    private Optional<Resolved> resolveFromEditor(MessageEditorHttpRequestResponse editor) {
        HttpRequest request = editor.requestResponse().request();
        if (request == null) {
            return Optional.empty();
        }
        String selectedText = EditorSelection.extractSelectedText(editor);
        String name = (selectedText != null && !selectedText.isBlank())
                ? nameGenerator.sanitizeForTabName(selectedText)
                : autoName(request);
        return Optional.of(new Resolved(request, name));
    }

    private Optional<Resolved> resolveFromSelectedRows(List<HttpRequestResponse> selected) {
        if (selected.isEmpty()) {
            return Optional.empty();
        }
        HttpRequest request = selected.get(0).request();
        if (request == null) {
            return Optional.empty();
        }
        return Optional.of(new Resolved(request, autoName(request)));
    }

    private String autoName(HttpRequest request) {
        return nameGenerator.generate(
                request.method(),
                request.pathWithoutQuery(),
                request.contentType().name(),
                request.bodyToString(),
                request.httpService() != null ? request.httpService().host() : null
        );
    }

    private record Resolved(HttpRequest request, String name) {
    }
}
