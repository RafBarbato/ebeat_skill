package util;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.model.Response;
import com.amazon.ask.model.interfaces.audioplayer.PlayBehavior;
import org.slf4j.Logger;
import service.AccountService;
import service.CurrentTrackService;
import service.RefreshService;

import java.util.Optional;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Logica condivisa "risolvi utente -&gt; leggi traccia -&gt; refresh URL se
 * scaduto -&gt; emetti AudioPlayer.Play". Usata da LaunchHandler e
 * MusicPlayIntentHandler per evitare duplicazione.
 *
 * Differenze tra modalita:
 * <ul>
 *   <li>{@link Mode#START} — invocazione "naturale" (LaunchRequest o
 *       MusicPlayIntent senza modifier): parte dall'offset salvato.</li>
 *   <li>{@link Mode#START_OVER} — AMAZON.StartOverIntent: forza offset = 0.</li>
 *   <li>{@link Mode#LOOP_ON} — AMAZON.LoopOnIntent: attiva loop_mode +
 *       parte dall'offset salvato.</li>
 *   <li>{@link Mode#RESUME} — AMAZON.ResumeIntent: parte dall'offset salvato,
 *       speech corto ("Riprendo.").</li>
 * </ul>
 */
public final class PlaybackStarter {

    private static final Logger LOG = getLogger(PlaybackStarter.class);

    public enum Mode { START, START_OVER, LOOP_ON, RESUME, SYNC }

    private PlaybackStarter() {}

    public static Optional<Response> start(HandlerInput input, Mode mode) {
        AccountService accountService = new AccountService();
        CurrentTrackService currentTrackService = new CurrentTrackService();
        RefreshService refreshService = new RefreshService();

        boolean startOver = mode == Mode.START_OVER;
        boolean loopOn    = mode == Mode.LOOP_ON;
        boolean resume    = mode == Mode.RESUME;
        boolean sync      = mode == Mode.SYNC;

        String accessToken = input.getRequestEnvelope().getContext().getSystem().getUser().getAccessToken();
        if (accessToken == null) {
            return input.getResponseBuilder()
                    .withSpeech("Per usare ebeat devi collegare il tuo account. Controlla l'app Alexa.")
                    .withLinkAccountCard()
                    .withShouldEndSession(true)
                    .build();
        }

        String email;
        try {
            email = accountService.resolveEmail(accessToken);
            LOG.info("Utente risolto: {}", email);
        } catch (Exception e) {
            LOG.error("Errore risoluzione utente [{}: {}]", e.getClass().getSimpleName(), e.getMessage());
            return input.getResponseBuilder()
                    .withSpeech("Errore nel riconoscere il tuo account. Riprova.")
                    .withShouldEndSession(true)
                    .build();
        }

        Optional<CurrentTrack> maybeTrack = currentTrackService.findByUserId(email);
        if (!maybeTrack.isPresent()) {
            return input.getResponseBuilder()
                    .withSpeech("Nessuna traccia trovata su ebeat. Avvia la riproduzione dall'app.")
                    .withShouldEndSession(true)
                    .build();
        }

        CurrentTrack track = maybeTrack.get();

        if (track.isExpired()) {
            if (track.getYoutube_id() == null) {
                return input.getResponseBuilder()
                        .withSpeech("Il link è scaduto e non posso aggiornarlo automaticamente. Riapri l'app ebeat.")
                        .withShouldEndSession(true)
                        .build();
            }
            try {
                LOG.info("URL scaduto, richiedo refresh per youtube_id={}", track.getYoutube_id());
                refreshService.refresh(email, track.getYoutube_id());
            } catch (Exception e) {
                LOG.error("Refresh fallito [{}: {}]", e.getClass().getSimpleName(), e.getMessage());
                return input.getResponseBuilder()
                        .withSpeech("Non sono riuscito ad aggiornare la traccia. Riprova tra poco.")
                        .withShouldEndSession(true)
                        .build();
            }

            Optional<CurrentTrack> refreshed = currentTrackService.findByUserId(email);
            if (!refreshed.isPresent() || refreshed.get().isExpired()) {
                return input.getResponseBuilder()
                        .withSpeech("Non sono riuscito ad aggiornare la traccia. Riprova tra poco.")
                        .withShouldEndSession(true)
                        .build();
            }
            track = refreshed.get();
        }

        long offset;
        if (startOver) {
            offset = 0L;
            try {
                currentTrackService.updateOffset(email, 0L);
            } catch (Exception e) {
                LOG.warn("Reset offset fallito [{}: {}]", e.getClass().getSimpleName(), e.getMessage());
            }
        } else {
            offset = track.getOffset() != null ? track.getOffset() : 0L;
            // Clamp: offset oltre la durata farebbe scattare
            // MEDIA_ERROR_SERVICE_UNAVAILABLE su Alexa.
            if (track.getTrack_duration() != null
                    && offset >= (track.getTrack_duration() - 2) * 1000L) {
                LOG.warn("Offset {} ms oltre durata {} s, reset a 0",
                        offset, track.getTrack_duration());
                offset = 0L;
            }
        }
        LOG.info("Offset (ms) usato: {}", offset);

        if (loopOn) {
            try {
                currentTrackService.setLoopMode(email, true);
            } catch (Exception e) {
                LOG.warn("Attivazione loop_mode fallita [{}: {}]", e.getClass().getSimpleName(), e.getMessage());
            }
        }

        String title = track.getTrack_title() != null ? track.getTrack_title() : "la tua musica";
        String artist = track.getTrack_artist() != null ? track.getTrack_artist() : "";

        String speech;
        if (loopOn) {
            speech = "Riproduco " + title + " in loop.";
        } else if (startOver) {
            speech = "Riavvio " + title + " da capo.";
        } else if (resume) {
            speech = "Riprendo.";
        } else if (sync) {
            speech = "Aggiorno.";
        } else if (artist.isEmpty()) {
            speech = "Riproduco " + title + " da ebeat.";
        } else {
            speech = "Riproduco " + title + " di " + artist + " da ebeat.";
        }

        return input.getResponseBuilder()
                .withSpeech(speech)
                .addAudioPlayerPlayDirective(PlayBehavior.REPLACE_ALL, offset, "", "ebeat", track.getUrl())
                .withShouldEndSession(true)
                .build();
    }
}
