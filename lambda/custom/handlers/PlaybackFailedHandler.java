package handlers;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.RequestHandler;
import com.amazon.ask.model.Response;
import com.amazon.ask.model.interfaces.audioplayer.PlayBehavior;
import com.amazon.ask.model.interfaces.audioplayer.PlaybackFailedRequest;
import org.slf4j.Logger;
import service.AccountService;
import service.CurrentTrackService;
import service.RefreshService;
import util.CurrentTrack;

import java.util.Optional;

import static org.slf4j.LoggerFactory.getLogger;

public class PlaybackFailedHandler implements RequestHandler {

    private static final Logger LOG = getLogger(PlaybackFailedHandler.class);
    private static final String RETRY_TOKEN_PREFIX = "ebeat-retry-";

    private final AccountService accountService = new AccountService();
    private final CurrentTrackService currentTrackService = new CurrentTrackService();
    private final RefreshService refreshService = new RefreshService();

    @Override
    public boolean canHandle(HandlerInput input) {
        return input.getRequest() instanceof PlaybackFailedRequest;
    }

    @Override
    public Optional<Response> handle(HandlerInput input) {
        LOG.info("PlaybackFailedHandler invocato");
        PlaybackFailedRequest req = (PlaybackFailedRequest) input.getRequest();
        Object type = req.getError() != null ? req.getError().getType() : null;
        String message = req.getError() != null ? req.getError().getMessage() : null;
        String token = req.getToken();
        LOG.error("PlaybackFailed [type={}, message={}, offset={}, token={}]",
                type, message, req.getCurrentPlaybackState() != null
                        ? req.getCurrentPlaybackState().getOffsetInMilliseconds() : null,
                token);

        if (token != null && token.startsWith(RETRY_TOKEN_PREFIX)) {
            LOG.warn("Failure su token retry, abbandono per evitare loop");
            return input.getResponseBuilder().build();
        }

        String accessToken = input.getRequestEnvelope().getContext().getSystem().getUser().getAccessToken();
        if (accessToken == null) {
            return input.getResponseBuilder().build();
        }

        try {
            String email = accountService.resolveEmail(accessToken);
            Optional<CurrentTrack> maybeTrack = currentTrackService.findByUserId(email);
            if (!maybeTrack.isPresent() || maybeTrack.get().getYoutube_id() == null) {
                return input.getResponseBuilder().build();
            }
            CurrentTrack track = maybeTrack.get();

            String oldUrl = track.getUrl();
            LOG.info("Refresh URL post-failure per youtube_id={} [oldUrl={}]",
                    track.getYoutube_id(), oldUrl);
            refreshService.refresh(email, track.getYoutube_id());

            Optional<CurrentTrack> refreshed = currentTrackService.findByUserId(email);
            if (!refreshed.isPresent() || refreshed.get().isExpired()) {
                LOG.warn("Refresh post-failure non ha prodotto un URL valido");
                return input.getResponseBuilder().build();
            }
            track = refreshed.get();
            String newUrl = track.getUrl();
            boolean changed = oldUrl != null && !oldUrl.equals(newUrl);
            LOG.info("Refresh esito [changed={}, newUrl={}]", changed, newUrl);

            long offset = track.getOffset() != null ? track.getOffset() : 0L;
            // Clamp coerente con MusicPlayIntentHandler: un retry con offset
            // oltre la durata farebbe rifallire la directive con lo stesso errore.
            if (track.getTrack_duration() != null
                    && offset >= (track.getTrack_duration() - 2) * 1000L) {
                LOG.warn("Offset {} ms oltre durata {} s nel retry, reset a 0",
                        offset, track.getTrack_duration());
                offset = 0L;
            }
            String newToken = RETRY_TOKEN_PREFIX + System.currentTimeMillis();
            LOG.info("Retry play post-failure [token={}, offset={}]", newToken, offset);
            return input.getResponseBuilder()
                    .addAudioPlayerPlayDirective(PlayBehavior.REPLACE_ALL, offset, "", newToken, track.getUrl())
                    .build();
        } catch (Exception e) {
            LOG.error("Retry post-failure fallito [{}: {}]", e.getClass().getSimpleName(), e.getMessage());
            return input.getResponseBuilder().build();
        }
    }
}
