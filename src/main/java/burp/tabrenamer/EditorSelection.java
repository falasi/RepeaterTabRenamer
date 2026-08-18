package burp.tabrenamer;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.core.Range;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse;

import java.util.Optional;

/** Pulls the currently selected text out of a Burp message editor selection, byte-accurate. */
final class EditorSelection {

    private EditorSelection() {
    }

    /** Returns the selected text, or null if there's no selection (or nothing to select from). */
    static String extractSelectedText(MessageEditorHttpRequestResponse editor) {
        Optional<Range> rangeOpt = editor.selectionOffsets();
        if (rangeOpt.isEmpty()) {
            return null;
        }

        HttpRequestResponse requestResponse = editor.requestResponse();
        ByteArray full = switch (editor.selectionContext()) {
            case REQUEST -> requestResponse.request() != null ? requestResponse.request().toByteArray() : null;
            case RESPONSE -> requestResponse.response() != null ? requestResponse.response().toByteArray() : null;
        };
        if (full == null) {
            return null;
        }

        return full.subArray(rangeOpt.get()).toString();
    }
}
