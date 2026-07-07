package handlers;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.RequestHandler;
import com.amazon.ask.model.Response;
import com.amazon.ask.model.interfaces.audioplayer.PlayBehavior;
import com.amazon.ask.model.interfaces.audioplayer.PlaybackNearlyFinishedRequest;
import org.slf4j.Logger;
import service.AccountService;
import service.CurrentTrackService;
import service.PlaybackQueueService;
import util.CurrentTrack;
import util.QueueItem;

import java.util.Optional;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Quando la traccia in riproduzione sta per finire, decide cosa fare dopo:
 * <ol>
 *   <li>se loop_mode = true → riaccoda la stessa traccia (caso 9);</li>
 *   <li>altrimenti, se c'è una riga in playback_queue, la promuove a
 *       current_track e la accoda con PlayBehavior.ENQUEUE (caso 7);</li>
 *   <li>altrimenti, niente — la riproduzione termina naturalmente.</li>
 * </ol>
 */
public class PlaybackNearlyFinishedHandler implements RequestHandler {

    private static final Logger LOG = getLogger(PlaybackNearlyFinishedHandler.class);
    private static final String LOOP_TOKEN_PREFIX = "ebeat-loop-";
    private static final String QUEUE_TOKEN_PREFIX = "ebeat-q-";

    private final AccountService accountService = new AccountService();
    private final CurrentTrackService currentTrackService = new CurrentTrackService();
    private final PlaybackQueueService queueService = new PlaybackQueueService();

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

            // 1. Loop attivo: riaccoda la stessa traccia.
            if (Boolean.TRUE.equals(track.getLoop_mode())) {
                return enqueueLoop(input, currentToken, email, track);
            }

            // 2. Coda non vuota: promuovi la prossima.
            Optional<QueueItem> next = queueService.findNext(email);
            if (next.isPresent()) {
                return enqueueFromQueue(input, currentToken, email, next.get());
            }

            // 3. Nulla da fare.
            LOG.info("Nessuna traccia successiva (queue vuota, loop disattivo)");
            return input.getResponseBuilder().build();

        } catch (Exception e) {
            LOG.error("Errore PlaybackNearlyFinished [{}: {}]",
                    e.getClass().getSimpleName(), e.getMessage());
            return input.getResponseBuilder().build();
        }
    }

    private Optional<Response> enqueueLoop(HandlerInput input, String currentToken,
                                           String email, CurrentTrack track) {
        // No refresh server-side: l'URL deve essere già fresca o l'app deve
        // aggiornarla. Se scaduta, loop si interrompe (silenzio fine traccia).
        if (track.getUrl() == null || track.isExpired()) {
            LOG.warn("Loop interrotto: URL null/scaduto, app deve aggiornare");
            return input.getResponseBuilder().build();
        }
        // Token = youtube_id (loop = stessa traccia). Vedi PlaybackStoppedHandler.
        String newToken = track.getYoutube_id() != null
                ? track.getYoutube_id()
                : LOOP_TOKEN_PREFIX + System.currentTimeMillis();
        LOG.info("Loop attivo: riaccodo traccia per {} [currentToken={}, newToken={}]",
                email, currentToken, newToken);
        return input.getResponseBuilder()
                .addAudioPlayerPlayDirective(
                        PlayBehavior.ENQUEUE, 0L, currentToken, newToken, track.getUrl())
                .build();
    }

    private Optional<Response> enqueueFromQueue(HandlerInput input, String currentToken,
                                                String email, QueueItem item) {
        // Se la prossima traccia non ha URL pre-risolto (app non ha ancora
        // popolato), salta la promozione e lascia la riga in coda. Niente
        // refresh server-side (vincolo IP residenziale).
        if (item.getUrl() == null) {
            LOG.warn("Prossima traccia senza URL pre-risolto (position={}), " +
                    "attesa app per refill — niente enqueue", item.getPosition());
            return input.getResponseBuilder().build();
        }

        LOG.info("Promuovo coda position={} youtube_id={} title={}",
                item.getPosition(), item.getYoutube_id(), item.getTrack_title());

        // Promuove la riga a current_track (azzera offset).
        try {
            currentTrackService.promoteFromQueue(email, item);
        } catch (Exception e) {
            LOG.error("Promotion in current_track fallita [{}: {}]",
                    e.getClass().getSimpleName(), e.getMessage());
            return input.getResponseBuilder().build();
        }

        // Cancella la riga dalla coda (best-effort).
        try {
            queueService.delete(email, item.getPosition());
        } catch (Exception e) {
            LOG.warn("Delete dalla coda fallito (non bloccante) [{}: {}]",
                    e.getClass().getSimpleName(), e.getMessage());
        }

        // Rilegge current_track con i nuovi metadati.
        Optional<CurrentTrack> reloaded = currentTrackService.findByUserId(email);
        if (!reloaded.isPresent()) {
            LOG.warn("current_track non leggibile dopo promotion");
            return input.getResponseBuilder().build();
        }
        CurrentTrack newTrack = reloaded.get();

        if (newTrack.getUrl() == null || newTrack.isExpired()) {
            LOG.warn("URL null/scaduta dopo promotion — niente enqueue");
            return input.getResponseBuilder().build();
        }

        // Token = youtube_id della traccia promossa. Vedi PlaybackStoppedHandler.
        String newToken = newTrack.getYoutube_id() != null
                ? newTrack.getYoutube_id()
                : QUEUE_TOKEN_PREFIX + System.currentTimeMillis();
        LOG.info("Enqueue prossima traccia [currentToken={}, newToken={}, title={}]",
                currentToken, newToken, newTrack.getTrack_title());
        return input.getResponseBuilder()
                .addAudioPlayerPlayDirective(
                        PlayBehavior.ENQUEUE, 0L, currentToken, newToken, newTrack.getUrl())
                .build();
    }
}
