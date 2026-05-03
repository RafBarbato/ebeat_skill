package handlers;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.impl.LaunchRequestHandler;
import com.amazon.ask.model.LaunchRequest;
import com.amazon.ask.model.Response;
import org.slf4j.Logger;
import util.PlaybackStarter;

import java.util.Optional;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Avvia direttamente la riproduzione dell'ultima traccia dell'utente non
 * appena la skill viene aperta ("Alexa, apri ebeat"). Equivalente
 * funzionalmente a un MusicPlayIntent senza modifier: stessa logica
 * delegata a {@link PlaybackStarter}.
 *
 * Se l'utente non è ancora autenticato, o non ha tracce, o l'URL è
 * irrecuperabile, PlaybackStarter ritorna direttamente la risposta vocale
 * appropriata e chiude la sessione.
 */
public class LaunchHandler implements LaunchRequestHandler {

    private static final Logger LOG = getLogger(LaunchHandler.class);

    @Override
    public boolean canHandle(HandlerInput handlerInput, LaunchRequest launchRequest) {
        return true;
    }

    @Override
    public Optional<Response> handle(HandlerInput handlerInput, LaunchRequest launchRequest) {
        LOG.info("LaunchHandler invocato — avvio riproduzione automatica");
        return PlaybackStarter.start(handlerInput, PlaybackStarter.Mode.START);
    }

}
