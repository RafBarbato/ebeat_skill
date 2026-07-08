package handlers;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.impl.IntentRequestHandler;
import com.amazon.ask.model.IntentRequest;
import com.amazon.ask.model.Response;
import org.slf4j.Logger;
import service.AccountService;
import service.CurrentTrackService;

import java.util.Optional;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Disattiva la modalità loop (AMAZON.LoopOffIntent), complemento di LoopOn
 * (gestito da MusicPlayIntentHandler): setta {@code loop_mode=false}. NON ferma
 * la riproduzione — l'audio continua, la traccia semplicemente non si riaccoda
 * più a fine brano (vedi PlaybackNearlyFinishedHandler). Richiesto per la
 * certificazione delle skill AudioPlayer.
 */
public class LoopOffIntentHandler implements IntentRequestHandler {

    private static final Logger LOG = getLogger(LoopOffIntentHandler.class);

    private final AccountService accountService = new AccountService();
    private final CurrentTrackService currentTrackService = new CurrentTrackService();

    @Override
    public boolean canHandle(HandlerInput handlerInput, IntentRequest intentRequest) {
        return intentRequest.getIntent().getName().equals("AMAZON.LoopOffIntent");
    }

    @Override
    public Optional<Response> handle(HandlerInput handlerInput, IntentRequest intentRequest) {
        LOG.info("LoopOffIntentHandler invocato");
        String accessToken = handlerInput.getRequestEnvelope().getContext().getSystem().getUser().getAccessToken();
        if (accessToken != null) {
            try {
                String email = accountService.resolveEmail(accessToken);
                currentTrackService.setLoopMode(email, false);
            } catch (Exception e) {
                LOG.warn("Disattivazione loop fallita [{}: {}]", e.getClass().getSimpleName(), e.getMessage());
            }
        }
        return handlerInput.getResponseBuilder()
                .withSpeech("Loop disattivato.")
                .withShouldEndSession(true)
                .build();
    }
}
