package handlers;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.RequestHandler;
import com.amazon.ask.model.Response;
import com.amazon.ask.model.interfaces.audioplayer.PlaybackStartedRequest;
import org.slf4j.Logger;
import service.AccountService;
import service.CurrentTrackService;
import service.PlaybackQueueService;
import service.RefillService;
import util.CurrentTrack;

import java.util.Optional;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Quando una nuova traccia inizia a suonare, controlla la coda e
 * rifornisce con tracce correlate al seed pinned se sotto soglia.
 * Caso 15 — auto-refill radio infinita.
 *
 * Trigger su {@code AudioPlayer.PlaybackStarted}: l'utente sta già
 * ascoltando, quindi i ~3-5 s della chiamata refill sono latenza
 * invisibile (l'handler non emette speech né directive).
 */
public class PlaybackStartedHandler implements RequestHandler {

    private static final Logger LOG = getLogger(PlaybackStartedHandler.class);
    private static final int REFILL_THRESHOLD = 1;

    private final AccountService accountService = new AccountService();
    private final CurrentTrackService currentTrackService = new CurrentTrackService();
    private final PlaybackQueueService queueService = new PlaybackQueueService();
    private final RefillService refillService = new RefillService();

    @Override
    public boolean canHandle(HandlerInput input) {
        return input.getRequest() instanceof PlaybackStartedRequest;
    }

    @Override
    public Optional<Response> handle(HandlerInput input) {
        LOG.info("PlaybackStartedHandler invocato");
        String accessToken = input.getRequestEnvelope().getContext().getSystem().getUser().getAccessToken();
        if (accessToken == null) {
            return input.getResponseBuilder().build();
        }

        try {
            String email = accountService.resolveEmail(accessToken);

            // Una traccia sta effettivamente suonando su Alexa: riafferma il
            // device attivo e is_playing=true (il PlaybackStopped emesso dal
            // REPLACE_ALL della traccia precedente lo aveva messo a false).
            // Così l'app riflette correttamente "in riproduzione".
            try {
                var device = input.getRequestEnvelope().getContext().getSystem().getDevice();
                String devId = device != null ? device.getDeviceId() : null;
                if (devId != null) {
                    currentTrackService.setPlaybackState(email, "alexa:" + devId, true);
                } else {
                    currentTrackService.setIsPlaying(email, true);
                }
            } catch (Exception e) {
                LOG.warn("setPlaybackState (started) fallito [{}: {}]",
                        e.getClass().getSimpleName(), e.getMessage());
            }

            int queueCount = queueService.countByUserId(email);
            if (queueCount > REFILL_THRESHOLD) {
                LOG.info("Queue count={}, oltre soglia ({}), nessun refill", queueCount, REFILL_THRESHOLD);
                return input.getResponseBuilder().build();
            }

            Optional<CurrentTrack> maybeTrack = currentTrackService.findByUserId(email);
            if (!maybeTrack.isPresent()) {
                LOG.info("current_track assente, niente refill");
                return input.getResponseBuilder().build();
            }
            Long seed = maybeTrack.get().getRadio_seed_track_id();
            if (seed == null) {
                LOG.info("radio_seed_track_id null, niente refill (sessione non-radio)");
                return input.getResponseBuilder().build();
            }

            LOG.info("Queue count={} <= {}, refill con seed={}", queueCount, REFILL_THRESHOLD, seed);
            refillService.refill(email, seed);
        } catch (Exception e) {
            LOG.warn("Refill in PlaybackStarted fallito [{}: {}]",
                    e.getClass().getSimpleName(), e.getMessage());
        }
        return input.getResponseBuilder().build();
    }
}
