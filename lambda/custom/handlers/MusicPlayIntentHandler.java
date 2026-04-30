package handlers;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.impl.IntentRequestHandler;
import com.amazon.ask.model.IntentRequest;
import com.amazon.ask.model.Response;
import com.amazon.ask.model.interfaces.audioplayer.PlayBehavior;
import org.slf4j.Logger;
import service.AccountService;
import service.CurrentTrackService;
import service.RefreshService;
import util.CurrentTrack;

import java.util.Optional;

import static org.slf4j.LoggerFactory.getLogger;

public class MusicPlayIntentHandler implements IntentRequestHandler {

    private static final Logger LOG = getLogger(MusicPlayIntentHandler.class);

    private final AccountService accountService = new AccountService();
    private final CurrentTrackService currentTrackService = new CurrentTrackService();
    private final RefreshService refreshService = new RefreshService();

    @Override
    public boolean canHandle(HandlerInput handlerInput, IntentRequest intentRequest) {
        String name = intentRequest.getIntent().getName();
        return name.equals("MusicPlayIntent")
                || name.equals("AMAZON.StartOverIntent")
                || name.equals("AMAZON.LoopOnIntent");
    }

    @Override
    public Optional<Response> handle(HandlerInput handlerInput, IntentRequest intentRequest) {
        String intentName = intentRequest.getIntent().getName();
        boolean startOver = intentName.equals("AMAZON.StartOverIntent");
        boolean loopOn    = intentName.equals("AMAZON.LoopOnIntent");

        String accessToken = handlerInput.getRequestEnvelope().getContext().getSystem().getUser().getAccessToken();
        if (accessToken == null) {
            return handlerInput.getResponseBuilder()
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
            return handlerInput.getResponseBuilder()
                    .withSpeech("Errore nel riconoscere il tuo account. Riprova.")
                    .withShouldEndSession(true)
                    .build();
        }

        Optional<CurrentTrack> maybeTrack = currentTrackService.findByUserId(email);
        if (!maybeTrack.isPresent()) {
            return handlerInput.getResponseBuilder()
                    .withSpeech("Nessuna traccia trovata su ebeat. Avvia la riproduzione dall'app.")
                    .withShouldEndSession(true)
                    .build();
        }

        CurrentTrack track = maybeTrack.get();

        if (track.isExpired()) {
            if (track.getYoutube_id() == null) {
                return handlerInput.getResponseBuilder()
                        .withSpeech("Il link è scaduto e non posso aggiornarlo automaticamente. Riapri l'app ebeat.")
                        .withShouldEndSession(true)
                        .build();
            }
            try {
                LOG.info("URL scaduto, richiedo refresh per youtube_id={}", track.getYoutube_id());
                refreshService.refresh(email, track.getYoutube_id());
            } catch (Exception e) {
                LOG.error("Refresh fallito [{}: {}]", e.getClass().getSimpleName(), e.getMessage());
                return handlerInput.getResponseBuilder()
                        .withSpeech("Non sono riuscito ad aggiornare la traccia. Riprova tra poco.")
                        .withShouldEndSession(true)
                        .build();
            }

            Optional<CurrentTrack> refreshed = currentTrackService.findByUserId(email);
            if (!refreshed.isPresent() || refreshed.get().isExpired()) {
                return handlerInput.getResponseBuilder()
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
        } else if (artist.isEmpty()) {
            speech = "Riproduco " + title + " da ebeat.";
        } else {
            speech = "Riproduco " + title + " di " + artist + " da ebeat.";
        }

        return handlerInput.getResponseBuilder()
                .withSpeech(speech)
                .addAudioPlayerPlayDirective(PlayBehavior.REPLACE_ALL, offset, "", "ebeat", track.getUrl())
                .withShouldEndSession(true)
                .build();
    }

}
