package handlers;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.RequestHandler;
import com.amazon.ask.model.Response;
import com.amazon.ask.model.interfaces.audioplayer.PlayBehavior;
import com.amazon.ask.model.interfaces.audioplayer.PlaybackNearlyFinishedRequest;
import org.slf4j.Logger;
import service.AccountService;
import service.CurrentTrackService;
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

    @Override
    public boolean canHandle(HandlerInput input) {
        return input.getRequest() instanceof PlaybackNearlyFinishedRequest;
    }

    @Override
    public Optional<Response> handle(HandlerInput input) {
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
            if (!Boolean.TRUE.equals(track.getLoop_mode()) || track.isExpired()) {
                return input.getResponseBuilder().build();
            }

            String newToken = "ebeat-loop-" + System.currentTimeMillis();
            LOG.info("Loop attivo: riaccodo traccia per {}", email);
            return input.getResponseBuilder()
                    .addAudioPlayerPlayDirective(
                            PlayBehavior.REPLACE_ENQUEUED,
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
