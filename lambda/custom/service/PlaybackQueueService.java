package service;

import org.springframework.web.client.RestClient;
import util.QueueItem;

import java.util.Optional;

/**
 * Accesso REST PostgREST alla tabella playback_queue (caso 7).
 *
 * L'URL endpoint viene derivato da SUPABASE_DB_TRACK_URI sostituendo
 * "/current_track" con "/playback_queue", per non aggiungere una nuova
 * env var alla skill.
 */
public class PlaybackQueueService {

    private final RestClient client;
    private final String queueUri;

    public PlaybackQueueService() {
        String key      = System.getenv("SUPABASE_SERVICE_KEY");
        String trackUri = System.getenv("SUPABASE_DB_TRACK_URI");
        this.queueUri   = trackUri != null
                ? trackUri.replace("/current_track", "/playback_queue")
                : null;

        this.client = RestClient.builder()
                .defaultHeader("apikey", key)
                .defaultHeader("Authorization", "Bearer " + key)
                .build();
    }

    /** Ritorna la prossima riga in coda (position minima) per l'utente. */
    public Optional<QueueItem> findNext(String userId) {
        if (queueUri == null) return Optional.empty();

        QueueItem[] body = client.get()
                .uri(queueUri + "?user_id=eq." + userId + "&order=position.asc&limit=1")
                .retrieve()
                .body(QueueItem[].class);

        if (body == null || body.length == 0) {
            return Optional.empty();
        }
        return Optional.of(body[0]);
    }

    /** Cancella la riga (user_id, position) dalla coda. */
    public void delete(String userId, int position) {
        if (queueUri == null) return;

        client.delete()
                .uri(queueUri + "?user_id=eq." + userId + "&position=eq." + position)
                .retrieve()
                .toBodilessEntity();
    }
}
