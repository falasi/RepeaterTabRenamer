package burp.tabrenamer;

import burp.api.montoya.core.ToolType;
import burp.api.montoya.http.handler.HttpHandler;
import burp.api.montoya.http.handler.HttpRequestToBeSent;
import burp.api.montoya.http.handler.HttpResponseReceived;
import burp.api.montoya.http.handler.RequestToBeSentAction;
import burp.api.montoya.http.handler.ResponseReceivedAction;
import burp.api.montoya.logging.Logging;

/** Watches for requests about to be sent from Repeater and renames their tab accordingly. */
public final class RepeaterRequestHandler implements HttpHandler {

    private final TabNameGenerator nameGenerator;
    private final RepeaterTabTitler tabTitler;
    private final Logging logging;

    public RepeaterRequestHandler(TabNameGenerator nameGenerator, RepeaterTabTitler tabTitler, Logging logging) {
        this.nameGenerator = nameGenerator;
        this.tabTitler = tabTitler;
        this.logging = logging;
    }

    @Override
    public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent requestToBeSent) {
        if (requestToBeSent.toolSource().isFromTool(ToolType.REPEATER)) {
            try {
                String name = nameGenerator.generate(
                        requestToBeSent.method(),
                        requestToBeSent.pathWithoutQuery(),
                        requestToBeSent.contentType().name(),
                        requestToBeSent.bodyToString(),
                        requestToBeSent.httpService() != null ? requestToBeSent.httpService().host() : null
                );
                tabTitler.renameActiveTab(name);
            } catch (Exception e) {
                logging.logToError("repeater-tab-renamer: failed to compute tab name", e);
            }
        }
        return RequestToBeSentAction.continueWith(requestToBeSent);
    }

    @Override
    public ResponseReceivedAction handleHttpResponseReceived(HttpResponseReceived responseReceived) {
        return ResponseReceivedAction.continueWith(responseReceived);
    }
}
