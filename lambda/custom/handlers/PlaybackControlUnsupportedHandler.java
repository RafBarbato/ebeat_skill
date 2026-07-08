package handlers;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.impl.IntentRequestHandler;
import com.amazon.ask.model.IntentRequest;
import com.amazon.ask.model.Response;
import org.slf4j.Logger;
import util.I18n;

import java.util.Optional;
import java.util.Set;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Gestisce i controlli AudioPlayer che ebeat non implementa — precedente,
 * ripeti, casuale on/off — con una risposta cortese e nessun errore. Amazon
 * richiede che una skill AudioPlayer gestisca TUTTI questi built-in per la
 * certificazione, anche solo dichiarando che la funzione non è disponibile.
 */
public class PlaybackControlUnsupportedHandler implements IntentRequestHandler {

    private static final Logger LOG = getLogger(PlaybackControlUnsupportedHandler.class);

    private static final Set<String> INTENTS = Set.of(
            "AMAZON.PreviousIntent",
            "AMAZON.RepeatIntent",
            "AMAZON.ShuffleOnIntent",
            "AMAZON.ShuffleOffIntent");

    @Override
    public boolean canHandle(HandlerInput handlerInput, IntentRequest intentRequest) {
        return INTENTS.contains(intentRequest.getIntent().getName());
    }

    @Override
    public Optional<Response> handle(HandlerInput handlerInput, IntentRequest intentRequest) {
        LOG.info("PlaybackControlUnsupportedHandler invocato [intent={}]",
                intentRequest.getIntent().getName());
        return handlerInput.getResponseBuilder()
                .withSpeech(I18n.t(intentRequest.getLocale(), "not_supported"))
                .withShouldEndSession(true)
                .build();
    }
}
