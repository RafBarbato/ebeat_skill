package handlers;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.impl.IntentRequestHandler;
import com.amazon.ask.model.IntentRequest;
import com.amazon.ask.model.Response;
import util.I18n;

import java.util.Optional;

public class HelpIntentHandler implements IntentRequestHandler {

    @Override
    public boolean canHandle(HandlerInput handlerInput, IntentRequest intentRequest) {
        return intentRequest.getIntent().getName().equals("AMAZON.HelpIntent");
    }

    @Override
    public Optional<Response> handle(HandlerInput handlerInput, IntentRequest intentRequest) {
        String locale = intentRequest.getLocale();
        return handlerInput.getResponseBuilder()
                .withSpeech(I18n.t(locale, "help_speech"))
                .withReprompt(I18n.t(locale, "help_reprompt"))
                .build();
    }

}
