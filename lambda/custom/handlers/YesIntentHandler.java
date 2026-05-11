package handlers;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.impl.IntentRequestHandler;
import com.amazon.ask.model.IntentRequest;
import com.amazon.ask.model.Response;
import com.amazon.ask.model.interfaces.audioplayer.PlayBehavior;
import org.slf4j.Logger;
import service.CurrentTrackService;
import service.RefreshService;
import service.YoutubeResolveService;
import util.CurrentTrack;
import util.DeezerTrack;

import java.util.Map;
import java.util.Optional;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Turn 2 della ricerca vocale (caso 15). Si attiva solo se
 * {@link PlayTrackIntentHandler} ha lasciato {@code pendingSearch} in
 * sessione. Risolve il youtube_id via BE, promuove su current_track,
 * rinfresca l'URL stream e riproduce.
 */
public class YesIntentHandler implements IntentRequestHandler {

    private static final Logger LOG = getLogger(YesIntentHandler.class);
    private static final String PLAY_TOKEN_PREFIX = "ebeat-search-";

    private final YoutubeResolveService resolveService = new YoutubeResolveService();
    private final CurrentTrackService currentTrackService = new CurrentTrackService();
    private final RefreshService refreshService = new RefreshService();

    @Override
    public boolean canHandle(HandlerInput handlerInput, IntentRequest intentRequest) {
        if (!intentRequest.getIntent().getName().equals("AMAZON.YesIntent")) return false;
        Map<String, Object> attrs = handlerInput.getAttributesManager().getSessionAttributes();
        return attrs != null && attrs.containsKey("pendingSearch");
    }

    @Override
    public Optional<Response> handle(HandlerInput handlerInput, IntentRequest intentRequest) {
        LOG.info("YesIntentHandler invocato (search confirm)");
        Map<String, Object> attrs = handlerInput.getAttributesManager().getSessionAttributes();
        @SuppressWarnings("unchecked")
        Map<String, Object> pending = (Map<String, Object>) attrs.get("pendingSearch");

        String title = (String) pending.get("title");
        String artist = (String) pending.get("artist");
        String email = (String) pending.get("email");
        Long duration = pending.get("duration") instanceof Number
                ? ((Number) pending.get("duration")).longValue() : null;
        Long deezerId = pending.get("deezer_id") instanceof Number
                ? ((Number) pending.get("deezer_id")).longValue() : null;

        // Cleanup sessione: qualunque sia l'esito, niente loop.
        attrs.remove("pendingSearch");
        handlerInput.getAttributesManager().setSessionAttributes(attrs);

        Optional<String> maybeYoutubeId;
        try {
            maybeYoutubeId = resolveService.resolveYoutubeId(title, artist, duration);
        } catch (Exception e) {
            LOG.error("Resolve YT id fallito [{}: {}]",
                    e.getClass().getSimpleName(), e.getMessage());
            return handlerInput.getResponseBuilder()
                    .withSpeech("Non riesco a trovare lo stream. Riprova più tardi.")
                    .withShouldEndSession(true)
                    .build();
        }
        if (!maybeYoutubeId.isPresent()) {
            return handlerInput.getResponseBuilder()
                    .withSpeech("Non ho trovato lo stream su YouTube. Prova un altro brano.")
                    .withShouldEndSession(true)
                    .build();
        }
        String youtubeId = maybeYoutubeId.get();
        LOG.info("Resolved youtube_id={} per title={} artist={}", youtubeId, title, artist);

        DeezerTrack t = new DeezerTrack();
        t.setId(deezerId);
        t.setTitle(title);
        t.setArtist(artist);
        t.setDuration(duration);
        try {
            currentTrackService.promoteFromSearch(email, t, youtubeId);
        } catch (Exception e) {
            LOG.error("promoteFromSearch fallita [{}: {}]",
                    e.getClass().getSimpleName(), e.getMessage());
            return handlerInput.getResponseBuilder()
                    .withSpeech("Non sono riuscito ad avviare il brano.")
                    .withShouldEndSession(true)
                    .build();
        }

        try {
            refreshService.refresh(email, youtubeId);
        } catch (Exception e) {
            LOG.error("refresh post-promote fallita [{}: {}]",
                    e.getClass().getSimpleName(), e.getMessage());
            return handlerInput.getResponseBuilder()
                    .withSpeech("Non sono riuscito a ottenere lo stream. Riprova.")
                    .withShouldEndSession(true)
                    .build();
        }

        Optional<CurrentTrack> reloaded = currentTrackService.findByUserId(email);
        if (!reloaded.isPresent() || reloaded.get().getUrl() == null) {
            return handlerInput.getResponseBuilder()
                    .withSpeech("Non sono riuscito a ottenere lo stream. Riprova.")
                    .withShouldEndSession(true)
                    .build();
        }
        CurrentTrack track = reloaded.get();

        String speech = artist != null
                ? "Riproduco " + title + " di " + artist + "."
                : "Riproduco " + title + ".";
        String token = PLAY_TOKEN_PREFIX + System.currentTimeMillis();

        return handlerInput.getResponseBuilder()
                .withSpeech(speech)
                .addAudioPlayerPlayDirective(PlayBehavior.REPLACE_ALL, 0L, "", token, track.getUrl())
                .withShouldEndSession(true)
                .build();
    }
}
