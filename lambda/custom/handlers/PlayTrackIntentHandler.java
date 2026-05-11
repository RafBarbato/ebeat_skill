package handlers;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.impl.IntentRequestHandler;
import com.amazon.ask.model.IntentRequest;
import com.amazon.ask.model.Response;
import com.amazon.ask.model.Slot;
import org.slf4j.Logger;
import service.AccountService;
import service.DeezerService;
import util.DeezerTrack;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Turn 1 della ricerca vocale (caso 15). L'utente dice "suona X di Y"
 * (artista opzionale); cerchiamo su Deezer, salviamo il primo hit in
 * sessione e chiediamo conferma. Il turn 2 è gestito da
 * {@link YesIntentHandler} / {@link NoIntentHandler}.
 */
public class PlayTrackIntentHandler implements IntentRequestHandler {

    private static final Logger LOG = getLogger(PlayTrackIntentHandler.class);

    private final AccountService accountService = new AccountService();
    private final DeezerService deezerService = new DeezerService();

    @Override
    public boolean canHandle(HandlerInput handlerInput, IntentRequest intentRequest) {
        return intentRequest.getIntent().getName().equals("PlayTrackIntent");
    }

    @Override
    public Optional<Response> handle(HandlerInput handlerInput, IntentRequest intentRequest) {
        LOG.info("PlayTrackIntentHandler invocato");

        String accessToken = handlerInput.getRequestEnvelope().getContext().getSystem().getUser().getAccessToken();
        if (accessToken == null) {
            return handlerInput.getResponseBuilder()
                    .withSpeech("Per usare ebeat devi collegare il tuo account.")
                    .withLinkAccountCard()
                    .withShouldEndSession(true)
                    .build();
        }

        Map<String, Slot> slots = intentRequest.getIntent().getSlots();
        String query = slotValue(slots, "query");

        if (query == null) {
            return handlerInput.getResponseBuilder()
                    .withSpeech("Quale canzone vuoi sentire?")
                    .withShouldEndSession(true)
                    .build();
        }
        LOG.info("Search query: {}", query);

        String email;
        try {
            email = accountService.resolveEmail(accessToken);
        } catch (Exception e) {
            LOG.error("Errore risoluzione utente [{}: {}]",
                    e.getClass().getSimpleName(), e.getMessage());
            return handlerInput.getResponseBuilder()
                    .withSpeech("Errore nel riconoscere il tuo account. Riprova.")
                    .withShouldEndSession(true)
                    .build();
        }

        Optional<DeezerTrack> maybe;
        try {
            maybe = deezerService.firstMatch(query);
        } catch (Exception e) {
            LOG.error("Deezer search fallita [{}: {}]",
                    e.getClass().getSimpleName(), e.getMessage());
            return handlerInput.getResponseBuilder()
                    .withSpeech("Non riesco a cercare ora, riprova.")
                    .withShouldEndSession(true)
                    .build();
        }

        if (!maybe.isPresent()) {
            return handlerInput.getResponseBuilder()
                    .withSpeech("Non ho trovato " + query + ". Prova un altro titolo.")
                    .withShouldEndSession(true)
                    .build();
        }
        DeezerTrack t = maybe.get();
        LOG.info("Deezer hit: id={} title={} artist={} duration={}",
                t.getId(), t.getTitle(), t.getArtist(), t.getDuration());

        Map<String, Object> pending = new HashMap<>();
        pending.put("deezer_id", t.getId());
        pending.put("title", t.getTitle());
        pending.put("artist", t.getArtist());
        pending.put("duration", t.getDuration());
        pending.put("email", email);

        Map<String, Object> sessionAttrs = handlerInput.getAttributesManager().getSessionAttributes();
        sessionAttrs.put("pendingSearch", pending);
        handlerInput.getAttributesManager().setSessionAttributes(sessionAttrs);

        String speech = t.getArtist() != null
                ? "Ho trovato " + t.getTitle() + " di " + t.getArtist() + ", va bene?"
                : "Ho trovato " + t.getTitle() + ", va bene?";

        return handlerInput.getResponseBuilder()
                .withSpeech(speech)
                .withReprompt("Va bene?")
                .withShouldEndSession(false)
                .build();
    }

    private static String slotValue(Map<String, Slot> slots, String name) {
        if (slots == null) return null;
        Slot s = slots.get(name);
        if (s == null || s.getValue() == null) return null;
        String v = s.getValue().trim();
        return v.isEmpty() ? null : v;
    }
}
