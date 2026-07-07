package handlers;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.RequestHandler;
import com.amazon.ask.model.Response;
import com.amazon.ask.model.interfaces.audioplayer.PlaybackStoppedRequest;
import org.slf4j.Logger;
import service.AccountService;
import service.CurrentTrackService;
import util.CurrentTrack;

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
        String stoppedToken = req.getToken();
        LOG.info("PlaybackStoppedHandler invocato [offset={}, token={}]", offset, stoppedToken);
        String accessToken = input.getRequestEnvelope().getContext().getSystem().getUser().getAccessToken();

        if (offset == null || accessToken == null) {
            LOG.warn("PlaybackStopped senza offset o accessToken (offset={}, hasToken={})",
                    offset, accessToken != null);
            return input.getResponseBuilder().build();
        }

        try {
            String email = accountService.resolveEmail(accessToken);

            // Il token della directive è lo youtube_id della traccia. Se il token
            // di QUESTO PlaybackStopped NON coincide con lo youtube_id corrente,
            // vuol dire che si è fermata una traccia GIÀ SUPERATA da un cambio
            // traccia (REPLACE_ALL: promoteFromQueue ha già messo la nuova in
            // current_track): non toccare nulla, ci pensa il PlaybackStarted della
            // nuova. Confronto per identità → immune alla corsa Stopped/Started
            // (evita anche di scrivere l'offset della vecchia sulla nuova traccia).
            Optional<CurrentTrack> cur = currentTrackService.findByUserId(email);
            String currentYid = cur.map(CurrentTrack::getYoutube_id).orElse(null);
            if (stoppedToken != null && currentYid != null && !stoppedToken.equals(currentYid)) {
                LOG.info("PlaybackStopped di traccia superata (token={} != current={}): skip",
                        stoppedToken, currentYid);
                return input.getResponseBuilder().build();
            }

            // Stop/pausa REALE della traccia corrente.
            // Offset solo se > 0 (offset=0 = probabile failure / stop a stream non
            // avviato: non sovrascrivere il punto di ripresa).
            if (offset > 0L) {
                currentTrackService.updateOffset(email, offset);
                LOG.info("Offset salvato per {}: {} ms", email, offset);
            } else {
                LOG.info("PlaybackStopped con offset=0: salto updateOffset");
            }
            // Rilascia il device: l'app riflette "su Alexa" SOLO mentre suona.
            // "Alexa stop" sull'audio in background arriva come PlaybackStopped
            // (non come StopIntent), quindi il rilascio va fatto qui.
            currentTrackService.setPlaybackState(email, null, false);
        } catch (Exception e) {
            LOG.error("Errore stato PlaybackStopped [{}: {}]", e.getClass().getSimpleName(), e.getMessage());
        }

        return input.getResponseBuilder().build();
    }
}
