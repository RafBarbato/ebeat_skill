package handlers;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.impl.IntentRequestHandler;
import com.amazon.ask.model.IntentRequest;
import com.amazon.ask.model.Response;
import org.slf4j.Logger;
import util.PlaybackStarter;

import java.util.Optional;

import static org.slf4j.LoggerFactory.getLogger;

public class MusicPlayIntentHandler implements IntentRequestHandler {

    private static final Logger LOG = getLogger(MusicPlayIntentHandler.class);

    @Override
    public boolean canHandle(HandlerInput handlerInput, IntentRequest intentRequest) {
        String name = intentRequest.getIntent().getName();
        // AMAZON.ResumeIntent ricade qui: la logica di "ripresa dall'offset"
        // è già quella di MusicPlayIntent, basta lasciare offset e URL come
        // sono in current_track (eventuale refresh URL se scaduto).
        return name.equals("MusicPlayIntent")
                || name.equals("AMAZON.StartOverIntent")
                || name.equals("AMAZON.LoopOnIntent")
                || name.equals("AMAZON.ResumeIntent");
    }

    @Override
    public Optional<Response> handle(HandlerInput handlerInput, IntentRequest intentRequest) {
        String intentName = intentRequest.getIntent().getName();
        LOG.info("MusicPlayIntentHandler invocato [intent={}]", intentName);

        PlaybackStarter.Mode mode;
        switch (intentName) {
            case "AMAZON.StartOverIntent":
                mode = PlaybackStarter.Mode.START_OVER;
                break;
            case "AMAZON.LoopOnIntent":
                mode = PlaybackStarter.Mode.LOOP_ON;
                break;
            case "AMAZON.ResumeIntent":
                mode = PlaybackStarter.Mode.RESUME;
                break;
            default:
                mode = PlaybackStarter.Mode.START;
        }
        return PlaybackStarter.start(handlerInput, mode);
    }

}
