package handlers;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.impl.IntentRequestHandler;
import com.amazon.ask.model.IntentRequest;
import com.amazon.ask.model.Response;
import util.IspUtil;
import util.SkillData;

import java.util.Optional;

public class SpazzaturaIntentHandler implements IntentRequestHandler {

    @Override
    public boolean canHandle(HandlerInput handlerInput, IntentRequest intentRequest) {
        return intentRequest.getIntent().getName().equals("SpazzaturaIntent");
    }

    @Override
    public Optional<Response> handle(HandlerInput handlerInput, IntentRequest intentRequest) {
        //final String repromptText = IspUtil.getRandomObject(SkillData.YES_NO_STRINGS);
        //final String speechText = String.format("%s %s", IspUtil.getRandomObject(SkillData.MUSIC_PLAY_STRINGS), repromptText);
        final String speechText = "Ilenia è una poco di buono!";
        return handlerInput.getResponseBuilder()
                .withSpeech(speechText)
                //.withReprompt(repromptText)
                .build();
    }

}
