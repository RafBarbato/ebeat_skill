package handlers;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.RequestHandler;
import com.amazon.ask.model.Response;
import com.amazon.ask.model.interfaces.audioplayer.PlayBehavior;
import com.amazon.ask.model.interfaces.audioplayer.PlaybackNearlyFinishedRequest;
import org.slf4j.Logger;
import service.AccountService;
import service.CurrentTrackService;
import service.RefreshService;
import util.CurrentTrack;

import java.util.Optional;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Quando il loop è attivo, riaccoda la stessa traccia poco prima che finisca
 * cosicché Alexa la riprenda dall'inizio senza interruzione.
 */
public class PlaybackNearlyFinishedHandler implements RequestHandler {

    private static final Logger LOG = getLogger(PlaybackNearlyFinishedHandler.class);

    private final AccountService accountService = new AccountService();
    private final CurrentTrackService currentTrackService = new CurrentTrackService();
    private final RefreshService refreshService = new RefreshService();

    @Override
    public boolean canHandle(HandlerInput input) {
        return input.getRequest() instanceof PlaybackNearlyFinishedRequest;
    }

    @Override
    public Optional<Response> handle(HandlerInput input) {
        LOG.info("PlaybackNearlyFinishedHandler invocato");
        PlaybackNearlyFinishedRequest req = (PlaybackNearlyFinishedRequest) input.getRequest();
        String currentToken = req.getToken();
        String accessToken = input.getRequestEnvelope().getContext().getSystem().getUser().getAccessToken();

        if (accessToken == null) {
            return input.getResponseBuilder().build();
        }

        try {
            String email = accountService.resolveEmail(accessToken);
            Optional<CurrentTrack> maybeTrack = currentTrackService.findByUserId(email);
            if (!maybeTrack.isPresent()) {
                return input.getResponseBuilder().build();
            }

            CurrentTrack track = maybeTrack.get();
            if (!Boolean.TRUE.equals(track.getLoop_mode())) {
                return input.getResponseBuilder().build();
            }

            if (track.getYoutube_id() != null) {
                try {
                    LOG.info("Refresh URL pre-loop per youtube_id={}", track.getYoutube_id());
                    refreshService.refresh(email, track.getYoutube_id());
                    Optional<CurrentTrack> refreshed = currentTrackService.findByUserId(email);
                    if (refreshed.isPresent()) {
                        track = refreshed.get();
                    }
                } catch (Exception e) {
                    LOG.warn("Refresh URL pre-loop fallito [{}: {}]",
                            e.getClass().getSimpleName(), e.getMessage());
                }
            }

            if (track.isExpired()) {
                LOG.warn("Loop interrotto: URL scaduto e refresh non disponibile");
                return input.getResponseBuilder().build();
            }

            String newToken = "ebeat-loop-" + System.currentTimeMillis();
            String urlPrefix = track.getUrl() != null && track.getUrl().length() > 80
                    ? track.getUrl().substring(0, 80) + "..." : track.getUrl();
            LOG.info("Loop attivo: riaccodo traccia per {} [currentToken={}, newToken={}, url={}]",
                    email, currentToken, newToken, urlPrefix);
            return input.getResponseBuilder()
                    .addAudioPlayerPlayDirective(
                            PlayBehavior.ENQUEUE,
                            0L,
                            currentToken,
                            newToken,
                            track.getUrl())
                    .build();
        } catch (Exception e) {
            LOG.error("Errore loop [{}: {}]", e.getClass().getSimpleName(), e.getMessage());
            return input.getResponseBuilder().build();
        }
    }
}
