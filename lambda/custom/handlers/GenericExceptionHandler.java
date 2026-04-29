package handlers;

import com.amazon.ask.dispatcher.exception.ExceptionHandler;
import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.model.Response;
import org.slf4j.Logger;

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
        LOG.error("Errore non gestito", throwable);
        final String speechText = "Si è verificato un errore. Riprova tra poco.";
        return input.getResponseBuilder()
                .withSpeech(speechText)
                .withShouldEndSession(true)
                .build();
    }
}
