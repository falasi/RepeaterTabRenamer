package burp.tabrenamer;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.core.Range;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse;

import java.util.Optional;

/**
 * Pulls the currently selected text out of a Burp message editor selection, byte-accurate.
 *
 * <p>Treated as untrusted input throughout: the bytes come from a response the tested site
 * controlled, or from a request the user may have edited into any shape, and the offsets come
 * from a live editor that can change under us. Every failure — a stale or out-of-range
 * selection, a message with no request/response, a decoding problem — returns null rather than
 * propagating, and the returned text is only ever used after {@link TabNameGenerator}
 * sanitizes and length-caps it.
 */
final class EditorSelection {

    private EditorSelection() {
    }

    /** Returns the selected text, or null if there's no usable selection. */
    static String extractSelectedText(MessageEditorHttpRequestResponse editor) {
        try {
            Optional<Range> rangeOpt = editor.selectionOffsets();
            if (rangeOpt.isEmpty()) {
                return null;
            }

            HttpRequestResponse requestResponse = editor.requestResponse();
            if (requestResponse == null) {
                return null;
            }
            ByteArray full = switch (editor.selectionContext()) {
                case REQUEST -> requestResponse.request() != null ? requestResponse.request().toByteArray() : null;
                case RESPONSE -> requestResponse.response() != null ? requestResponse.response().toByteArray() : null;
            };
            if (full == null) {
                return null;
            }

            // The editor's offsets and the message we just read are fetched separately, so they
            // can disagree if the user edits between the two calls. Bounds are re-checked here
            // rather than trusted.
            Range range = rangeOpt.get();
            int start = Math.max(0, range.startIndexInclusive());
            int end = Math.min(full.length(), range.endIndexExclusive());
            if (start >= end) {
                return null;
            }

            return full.subArray(start, end).toString();
        } catch (Exception e) {
            // Callers treat null as "nothing selected", which is the safe outcome: no rename.
            return null;
        }
    }
}
