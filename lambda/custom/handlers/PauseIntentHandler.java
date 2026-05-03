package handlers;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.impl.IntentRequestHandler;
import com.amazon.ask.model.IntentRequest;
import com.amazon.ask.model.Response;
import org.slf4j.Logger;

import java.util.Optional;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Mette in pausa la riproduzione conservando il punto attuale.
 *
 * Differenza con CancelAndStopIntentHandler: questo handler NON chiude la
 * skill e NON dice "A presto!". L'AudioPlayer.Stop directive ferma lo
 * stream; subito dopo Alexa emette PlaybackStopped che il PlaybackStoppedHandler
 * intercetta per persistere l'offset su current_track. Quando l'utente dice
 * "Alexa, riprendi", il MusicPlayIntentHandler legge l'offset e riparte
 * (vedi AMAZON.ResumeIntent).
 */
public class PauseIntentHandler implements IntentRequestHandler {

    private static final Logger LOG = getLogger(PauseIntentHandler.class);

    @Override
    public boolean canHandle(HandlerInput handlerInput, IntentRequest intentRequest) {
        return intentRequest.getIntent().getName().equals("AMAZON.PauseIntent");
    }

    @Override
    public Optional<Response> handle(HandlerInput handlerInput, IntentRequest intentRequest) {
        LOG.info("PauseIntentHandler invocato");
        return handlerInput.getResponseBuilder()
                .addAudioPlayerStopDirective()
                .build();
    }
}
