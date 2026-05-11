package handlers;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.impl.IntentRequestHandler;
import com.amazon.ask.model.IntentRequest;
import com.amazon.ask.model.Response;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Optional;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Annulla la ricerca proposta dal turn 1 di {@link PlayTrackIntentHandler}.
 * Si attiva solo se {@code pendingSearch} è presente in sessione, così non
 * intercetta i "no" generici al di fuori del contesto search (caso 15).
 */
public class NoIntentHandler implements IntentRequestHandler {

    private static final Logger LOG = getLogger(NoIntentHandler.class);

    @Override
    public boolean canHandle(HandlerInput handlerInput, IntentRequest intentRequest) {
        if (!intentRequest.getIntent().getName().equals("AMAZON.NoIntent")) return false;
        Map<String, Object> attrs = handlerInput.getAttributesManager().getSessionAttributes();
        return attrs != null && attrs.containsKey("pendingSearch");
    }

    @Override
    public Optional<Response> handle(HandlerInput handlerInput, IntentRequest intentRequest) {
        LOG.info("NoIntentHandler invocato (search abort)");
        Map<String, Object> attrs = handlerInput.getAttributesManager().getSessionAttributes();
        attrs.remove("pendingSearch");
        handlerInput.getAttributesManager().setSessionAttributes(attrs);

        return handlerInput.getResponseBuilder()
                .withSpeech("Ok, dimmi un altro brano.")
                .withShouldEndSession(true)
                .build();
    }
}
