package handlers;

import com.amazon.ask.dispatcher.exception.ExceptionHandler;
import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.model.Response;
import org.slf4j.Logger;
import util.I18n;

import java.util.Optional;

import static org.slf4j.LoggerFactory.getLogger;

public class GenericExceptionHandler implements ExceptionHandler {

    private static Logger LOG = getLogger(GenericExceptionHandler.class);

    @Override
    public boolean canHandle(HandlerInput input, Throwable throwable) {
        return true;
    }

    @Override
    public Optional<Response> handle(HandlerInput input, Throwable throwable) {
        String requestType = input.getRequest() != null ? input.getRequest().getType() : "?";
        LOG.error("Errore non gestito [requestType={}, throwableClass={}]: {}",
                requestType,
                throwable.getClass().getName(),
                throwable.getMessage(),
                throwable);

        // Eventi AudioPlayer / PlaybackController: nessuna sessione vocale attiva.
        // Un errore qui (es. BE irraggiungibile durante un PlaybackNearlyFinished)
        // NON deve produrre uno speech: risposta vuota → la riproduzione finisce in
        // silenzio, coerente col vincolo di progetto. Mai un errore vocale su un
        // evento di background (importante anche per la certificazione).
        if (requestType != null
                && (requestType.startsWith("AudioPlayer.")
                        || requestType.startsWith("PlaybackController."))) {
            return input.getResponseBuilder().build();
        }

        // Richieste vocali (Launch/Intent): messaggio cortese + chiusura pulita.
        String locale = input.getRequestEnvelope().getRequest() != null
                ? input.getRequestEnvelope().getRequest().getLocale() : null;
        return input.getResponseBuilder()
                .withSpeech(I18n.t(locale, "generic_error"))
                .withShouldEndSession(true)
                .build();
    }
}
