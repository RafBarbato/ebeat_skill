package handlers;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.impl.IntentRequestHandler;
import com.amazon.ask.model.IntentRequest;
import com.amazon.ask.model.Response;

import java.util.Optional;

public class CancelAndStopIntentHandler implements IntentRequestHandler {

    @Override
    public boolean canHandle(HandlerInput handlerInput, IntentRequest intentRequest) {
        String name = intentRequest.getIntent().getName();
        return name.equals("AMAZON.StopIntent")
                || name.equals("AMAZON.CancelIntent")
                || name.equals("AMAZON.PauseIntent");
    }

    @Override
    public Optional<Response> handle(HandlerInput handlerInput, IntentRequest intentRequest) {
        return handlerInput.getResponseBuilder()
                .addAudioPlayerStopDirective()
                .withSpeech("A presto!")
                .withShouldEndSession(true)
                .build();
    }

}
