package handlers;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.RequestHandler;
import com.amazon.ask.model.Response;
import com.amazon.ask.model.interfaces.audioplayer.PlaybackFailedRequest;
import org.slf4j.Logger;

import java.util.Optional;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Logga i fallimenti di riproduzione AudioPlayer. Nessun retry/refresh
 * server-side: la skill non chiama più YouTube (vincolo IP residenziale) e
 * l'endpoint di refresh del BE è deprecato (410 Gone). Il refresh dell'URL è
 * responsabilità dell'app; qui restiamo in silenzio (nessuna directive), come
 * per gli altri eventi AudioPlayer.
 */
public class PlaybackFailedHandler implements RequestHandler {

    private static final Logger LOG = getLogger(PlaybackFailedHandler.class);

    @Override
    public boolean canHandle(HandlerInput input) {
        return input.getRequest() instanceof PlaybackFailedRequest;
    }

    @Override
    public Optional<Response> handle(HandlerInput input) {
        PlaybackFailedRequest req = (PlaybackFailedRequest) input.getRequest();
        Object type = req.getError() != null ? req.getError().getType() : null;
        String message = req.getError() != null ? req.getError().getMessage() : null;
        Long offset = req.getCurrentPlaybackState() != null
                ? req.getCurrentPlaybackState().getOffsetInMilliseconds() : null;
        LOG.error("PlaybackFailed [type={}, message={}, offset={}, token={}]",
                type, message, offset, req.getToken());
        return input.getResponseBuilder().build();
    }
}
