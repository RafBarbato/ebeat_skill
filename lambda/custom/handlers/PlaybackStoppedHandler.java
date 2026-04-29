package handlers;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.RequestHandler;
import com.amazon.ask.model.Response;
import com.amazon.ask.model.interfaces.audioplayer.PlaybackStoppedRequest;
import org.slf4j.Logger;
import service.AccountService;
import service.CurrentTrackService;

import java.util.Optional;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Persiste l'offset corrente quando la riproduzione viene fermata
 * (Alexa stop / pause / fine sessione audio).
 */
public class PlaybackStoppedHandler implements RequestHandler {

    private static final Logger LOG = getLogger(PlaybackStoppedHandler.class);

    private final AccountService accountService = new AccountService();
    private final CurrentTrackService currentTrackService = new CurrentTrackService();

    @Override
    public boolean canHandle(HandlerInput input) {
        return input.getRequest() instanceof PlaybackStoppedRequest;
    }

    @Override
    public Optional<Response> handle(HandlerInput input) {
        PlaybackStoppedRequest req = (PlaybackStoppedRequest) input.getRequest();
        Long offset = req.getOffsetInMilliseconds();
        String accessToken = input.getRequestEnvelope().getContext().getSystem().getUser().getAccessToken();

        if (offset == null || accessToken == null) {
            LOG.warn("PlaybackStopped senza offset o accessToken (offset={}, hasToken={})",
                    offset, accessToken != null);
            return input.getResponseBuilder().build();
        }

        try {
            String email = accountService.resolveEmail(accessToken);
            currentTrackService.updateOffset(email, offset);
            LOG.info("Offset salvato per {}: {} ms", email, offset);
        } catch (Exception e) {
            LOG.error("Errore salvataggio offset [{}: {}]", e.getClass().getSimpleName(), e.getMessage());
        }

        return input.getResponseBuilder().build();
    }
}
