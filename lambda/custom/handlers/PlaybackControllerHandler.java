package handlers;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.RequestHandler;
import com.amazon.ask.model.Response;
import com.amazon.ask.model.interfaces.audioplayer.PlayBehavior;
import com.amazon.ask.model.interfaces.playbackcontroller.NextCommandIssuedRequest;
import com.amazon.ask.model.interfaces.playbackcontroller.PauseCommandIssuedRequest;
import com.amazon.ask.model.interfaces.playbackcontroller.PlayCommandIssuedRequest;
import com.amazon.ask.model.interfaces.playbackcontroller.PreviousCommandIssuedRequest;
import org.slf4j.Logger;
import service.AccountService;
import service.CurrentTrackService;
import service.PlaybackQueueService;
import util.CurrentTrack;
import util.QueueItem;

import java.util.Optional;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Gestisce i comandi da pulsanti hardware / card "in riproduzione" dell'app
 * Alexa (interfaccia PlaybackController): Play / Pause / Next / Previous.
 * Nessuno speech — sono pressioni di tasti, non comandi vocali. Richiesto per
 * la certificazione delle skill AudioPlayer: senza, i tasti della card non
 * farebbero nulla. Mai un crash: in caso di errore risposta vuota (silenzio).
 *
 *  - PauseCommandIssued    → AudioPlayer.Stop (l'offset lo salva PlaybackStopped)
 *  - PlayCommandIssued      → riprende current_track dall'offset salvato
 *  - NextCommandIssued      → promuove la prossima dalla coda (come "Alexa prossima")
 *  - PreviousCommandIssued  → non supportato (no-op): niente coda "indietro"
 */
public class PlaybackControllerHandler implements RequestHandler {

    private static final Logger LOG = getLogger(PlaybackControllerHandler.class);

    private final AccountService accountService = new AccountService();
    private final CurrentTrackService currentTrackService = new CurrentTrackService();
    private final PlaybackQueueService queueService = new PlaybackQueueService();

    @Override
    public boolean canHandle(HandlerInput input) {
        Object r = input.getRequest();
        return r instanceof PlayCommandIssuedRequest
                || r instanceof PauseCommandIssuedRequest
                || r instanceof NextCommandIssuedRequest
                || r instanceof PreviousCommandIssuedRequest;
    }

    @Override
    public Optional<Response> handle(HandlerInput input) {
        Object r = input.getRequest();

        if (r instanceof PauseCommandIssuedRequest) {
            LOG.info("PlaybackController: Pause");
            return input.getResponseBuilder().addAudioPlayerStopDirective().build();
        }
        if (r instanceof PreviousCommandIssuedRequest) {
            LOG.info("PlaybackController: Previous (non supportato, no-op)");
            return input.getResponseBuilder().build();
        }

        // Play e Next richiedono l'utente: se non collegato, no-op silenzioso.
        String accessToken = input.getRequestEnvelope().getContext().getSystem().getUser().getAccessToken();
        if (accessToken == null) {
            LOG.warn("PlaybackController senza accessToken: no-op");
            return input.getResponseBuilder().build();
        }

        try {
            String email = accountService.resolveEmail(accessToken);
            if (r instanceof NextCommandIssuedRequest) {
                LOG.info("PlaybackController: Next");
                return next(input, email);
            }
            LOG.info("PlaybackController: Play");
            return play(input, email);
        } catch (Exception e) {
            LOG.error("PlaybackController fallito [{}: {}]", e.getClass().getSimpleName(), e.getMessage());
            return input.getResponseBuilder().build(); // silenzio, mai crash
        }
    }

    /** Riprende la traccia corrente dall'offset salvato (nessuno speech). */
    private Optional<Response> play(HandlerInput input, String email) {
        Optional<CurrentTrack> maybe = currentTrackService.findByUserId(email);
        if (!maybe.isPresent()) return input.getResponseBuilder().build();
        CurrentTrack t = maybe.get();
        if (t.getUrl() == null || t.isExpired()) return input.getResponseBuilder().build();

        long offset = t.getOffset() != null ? t.getOffset() : 0L;
        // Clamp coerente con PlaybackStarter: offset oltre la durata farebbe
        // fallire la directive.
        if (t.getTrack_duration() != null && offset >= (t.getTrack_duration() - 2) * 1000L) {
            offset = 0L;
        }
        String token = t.getYoutube_id() != null ? t.getYoutube_id() : "ebeat";
        return input.getResponseBuilder()
                .addAudioPlayerPlayDirective(PlayBehavior.REPLACE_ALL, offset, "", token, t.getUrl())
                .build();
    }

    /** Promuove la prossima traccia dalla coda (come NextIntent, ma silenzioso). */
    private Optional<Response> next(HandlerInput input, String email) {
        Optional<QueueItem> maybe = queueService.findNext(email);
        if (!maybe.isPresent() || maybe.get().getUrl() == null) {
            return input.getResponseBuilder().build();
        }
        QueueItem item = maybe.get();
        try {
            currentTrackService.promoteFromQueue(email, item);
            queueService.delete(email, item.getPosition());
        } catch (Exception e) {
            LOG.warn("Next controller: promote/delete fallito [{}: {}]",
                    e.getClass().getSimpleName(), e.getMessage());
            return input.getResponseBuilder().build();
        }
        Optional<CurrentTrack> reloaded = currentTrackService.findByUserId(email);
        if (!reloaded.isPresent() || reloaded.get().getUrl() == null || reloaded.get().isExpired()) {
            return input.getResponseBuilder().build();
        }
        CurrentTrack nt = reloaded.get();
        String token = nt.getYoutube_id() != null ? nt.getYoutube_id() : "ebeat";
        return input.getResponseBuilder()
                .addAudioPlayerPlayDirective(PlayBehavior.REPLACE_ALL, 0L, "", token, nt.getUrl())
                .build();
    }
}
