package handlers;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.RequestHandler;
import com.amazon.ask.model.Response;
import com.amazon.ask.model.interfaces.audioplayer.PlaybackFinishedRequest;
import com.amazon.ask.model.interfaces.audioplayer.PlaybackStartedRequest;
import org.slf4j.Logger;

import java.util.Optional;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Logga PlaybackStarted e PlaybackFinished per diagnostica del flow audio.
 * Non altera il comportamento (ritorna response vuota).
 */
public class PlaybackEventLogHandler implements RequestHandler {

    private static final Logger LOG = getLogger(PlaybackEventLogHandler.class);

    @Override
    public boolean canHandle(HandlerInput input) {
        return input.getRequest() instanceof PlaybackStartedRequest
                || input.getRequest() instanceof PlaybackFinishedRequest;
    }

    @Override
    public Optional<Response> handle(HandlerInput input) {
        Object req = input.getRequest();
        if (req instanceof PlaybackStartedRequest) {
            PlaybackStartedRequest r = (PlaybackStartedRequest) req;
            LOG.info("PlaybackStarted [token={}, offset={}]", r.getToken(), r.getOffsetInMilliseconds());
        } else if (req instanceof PlaybackFinishedRequest) {
            PlaybackFinishedRequest r = (PlaybackFinishedRequest) req;
            LOG.info("PlaybackFinished [token={}, offset={}]", r.getToken(), r.getOffsetInMilliseconds());
        }
        return input.getResponseBuilder().build();
    }
}
