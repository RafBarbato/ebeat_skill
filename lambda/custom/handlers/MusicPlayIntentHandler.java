package handlers;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.impl.IntentRequestHandler;
import com.amazon.ask.model.IntentRequest;
import com.amazon.ask.model.Response;
import com.amazon.ask.model.interfaces.audioplayer.PlayBehavior;
import org.slf4j.Logger;
import service.AccountService;
import service.CurrentTrackService;
import util.CurrentTrack;

import java.util.Optional;

import static org.slf4j.LoggerFactory.getLogger;

public class MusicPlayIntentHandler implements IntentRequestHandler {

    private static final Logger LOG = getLogger(MusicPlayIntentHandler.class);

    private final AccountService accountService = new AccountService();
    private final CurrentTrackService currentTrackService = new CurrentTrackService();

    @Override
    public boolean canHandle(HandlerInput handlerInput, IntentRequest intentRequest) {
        return intentRequest.getIntent().getName().equals("MusicPlayIntent");
    }

    @Override
    public Optional<Response> handle(HandlerInput handlerInput, IntentRequest intentRequest) {
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
            return handlerInput.getResponseBuilder()
                    .withSpeech("Il link della traccia è scaduto. Riapri l'app ebeat per aggiornarlo.")
                    .withShouldEndSession(true)
                    .build();
        }

        long offset = track.getOffset() != null ? track.getOffset() : 0L;
        String title = track.getTrack_title() != null ? track.getTrack_title() : "la tua musica";
        String artist = track.getTrack_artist() != null ? track.getTrack_artist() : "";

        String speech = artist.isEmpty()
                ? "Riproduco " + title + " da ebeat."
                : "Riproduco " + title + " di " + artist + " da ebeat.";

        return handlerInput.getResponseBuilder()
                .withSpeech(speech)
                .addAudioPlayerPlayDirective(PlayBehavior.REPLACE_ALL, offset, "", "ebeat", track.getUrl())
                .withShouldEndSession(true)
                .build();
    }

}
