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

public class CancelAndStopIntentHandler implements IntentRequestHandler {

    private static final Logger LOG = getLogger(CancelAndStopIntentHandler.class);

    private final AccountService accountService = new AccountService();
    private final CurrentTrackService currentTrackService = new CurrentTrackService();

    @Override
    public boolean canHandle(HandlerInput handlerInput, IntentRequest intentRequest) {
        String name = intentRequest.getIntent().getName();
        return name.equals("AMAZON.StopIntent")
                || name.equals("AMAZON.CancelIntent")
                || name.equals("AMAZON.PauseIntent");
    }

    @Override
    public Optional<Response> handle(HandlerInput handlerInput, IntentRequest intentRequest) {
        String accessToken = handlerInput.getRequestEnvelope().getContext().getSystem().getUser().getAccessToken();
        if (accessToken != null) {
            try {
                String email = accountService.resolveEmail(accessToken);
                currentTrackService.setLoopMode(email, false);
            } catch (Exception e) {
                LOG.warn("Disattivazione loop_mode fallita [{}: {}]", e.getClass().getSimpleName(), e.getMessage());
            }
        }

        return handlerInput.getResponseBuilder()
                .addAudioPlayerStopDirective()
                .withSpeech("A presto!")
                .withShouldEndSession(true)
                .build();
    }

}
