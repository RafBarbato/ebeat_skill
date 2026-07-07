package handlers;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.impl.IntentRequestHandler;
import com.amazon.ask.model.IntentRequest;
import com.amazon.ask.model.Response;
import com.amazon.ask.model.interfaces.audioplayer.PlayBehavior;
import org.slf4j.Logger;
import service.AccountService;
import service.CurrentTrackService;
import service.PlaybackQueueService;
import util.CurrentTrack;
import util.QueueItem;

import java.util.Optional;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Skip vocale alla traccia successiva ("Alexa, prossima" /
 * "Alexa, avanti" / "Alexa, salta"). Differenza con
 * PlaybackNearlyFinishedHandler: qui interrompiamo la traccia corrente con
 * PlayBehavior.REPLACE_ALL, mentre il flusso automatico usa ENQUEUE per
 * concatenare senza tagli.
 *
 * Caso 7 - parte vocale.
 */
public class NextIntentHandler implements IntentRequestHandler {

    private static final Logger LOG = getLogger(NextIntentHandler.class);
    private static final String NEXT_TOKEN_PREFIX = "ebeat-next-";

    private final AccountService accountService = new AccountService();
    private final CurrentTrackService currentTrackService = new CurrentTrackService();
    private final PlaybackQueueService queueService = new PlaybackQueueService();

    @Override
    public boolean canHandle(HandlerInput handlerInput, IntentRequest intentRequest) {
        return intentRequest.getIntent().getName().equals("AMAZON.NextIntent");
    }

    @Override
    public Optional<Response> handle(HandlerInput handlerInput, IntentRequest intentRequest) {
        LOG.info("NextIntentHandler invocato");
        String accessToken = handlerInput.getRequestEnvelope().getContext().getSystem().getUser().getAccessToken();
        if (accessToken == null) {
            return handlerInput.getResponseBuilder()
                    .withSpeech("Per usare ebeat devi collegare il tuo account.")
                    .withShouldEndSession(true)
                    .build();
        }

        try {
            String email = accountService.resolveEmail(accessToken);

            Optional<QueueItem> next = queueService.findNext(email);
            if (!next.isPresent()) {
                LOG.info("Coda vuota, niente da skippare");
                return handlerInput.getResponseBuilder()
                        .withSpeech("Non ci sono altre tracce in coda.")
                        .withShouldEndSession(true)
                        .build();
            }
            QueueItem item = next.get();

            // Se l'URL non è pre-risolto (app non ha ancora popolato),
            // niente refresh server-side: chiediamo all'utente di aggiornare.
            if (item.getUrl() == null) {
                LOG.warn("Prossima traccia senza URL (position={}): app deve aggiornare",
                        item.getPosition());
                return handlerInput.getResponseBuilder()
                        .withSpeech("La prossima traccia non è ancora pronta. Apri l'app ebeat per aggiornare la coda.")
                        .withShouldEndSession(true)
                        .build();
            }

            LOG.info("Skip vocale: promuovo coda position={} youtube_id={} title={}",
                    item.getPosition(), item.getYoutube_id(), item.getTrack_title());

            // Promuove la riga in current_track (azzera offset).
            try {
                currentTrackService.promoteFromQueue(email, item);
            } catch (Exception e) {
                LOG.error("Promotion in current_track fallita [{}: {}]",
                        e.getClass().getSimpleName(), e.getMessage());
                return handlerInput.getResponseBuilder()
                        .withSpeech("Non sono riuscito a passare alla prossima.")
                        .withShouldEndSession(true)
                        .build();
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
                return handlerInput.getResponseBuilder().build();
            }
            CurrentTrack newTrack = reloaded.get();

            if (newTrack.getUrl() == null || newTrack.isExpired()) {
                LOG.warn("URL null/scaduto dopo promote — app deve aggiornare");
                return handlerInput.getResponseBuilder()
                        .withSpeech("La traccia non è aggiornata. Apri l'app ebeat per aggiornarla.")
                        .withShouldEndSession(true)
                        .build();
            }

            // Skip silenzioso: directive Play senza speech, l'utente sente
            // direttamente la nuova traccia partire.
            // Token = youtube_id (vedi PlaybackStoppedHandler: distingue lo stop
            // reale della corrente dal PlaybackStopped della traccia superata).
            String newToken = newTrack.getYoutube_id() != null
                    ? newTrack.getYoutube_id()
                    : NEXT_TOKEN_PREFIX + System.currentTimeMillis();
            return handlerInput.getResponseBuilder()
                    .addAudioPlayerPlayDirective(
                            PlayBehavior.REPLACE_ALL, 0L, "", newToken, newTrack.getUrl())
                    .withShouldEndSession(true)
                    .build();

        } catch (Exception e) {
            LOG.error("Errore NextIntentHandler [{}: {}]",
                    e.getClass().getSimpleName(), e.getMessage());
            return handlerInput.getResponseBuilder()
                    .withSpeech("Si e' verificato un errore.")
                    .withShouldEndSession(true)
                    .build();
        }
    }
}
