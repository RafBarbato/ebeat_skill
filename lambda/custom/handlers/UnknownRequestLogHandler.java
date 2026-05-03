package handlers;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.RequestHandler;
import com.amazon.ask.model.Request;
import com.amazon.ask.model.Response;
import org.slf4j.Logger;

import java.util.Optional;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Catch-all: registrato per ULTIMO. Logga il tipo di request quando nessun
 * altro handler lo gestisce — utile per scoprire eventi come
 * System.ExceptionEncountered, SessionEndedRequest, ecc.
 */
public class UnknownRequestLogHandler implements RequestHandler {

    private static final Logger LOG = getLogger(UnknownRequestLogHandler.class);

    @Override
    public boolean canHandle(HandlerInput input) {
        return true;
    }

    @Override
    public Optional<Response> handle(HandlerInput input) {
        Request req = input.getRequest();
        String type = req != null ? req.getType() : "?";
        LOG.warn("Unhandled request type [{}] - raw={}", type, req);
        return input.getResponseBuilder().build();
    }
}
