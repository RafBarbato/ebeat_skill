package service;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import util.BackendAlexaClient;
import util.QueueItem;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Accesso a {@code playback_queue} (caso 7) tramite gli endpoint interni del BE
 * ({@code /v1/internal/alexa/queue/*}). La skill non parla più a Supabase
 * (Fase 3 migrazione Redis).
 */
public class PlaybackQueueService {

    private final RestClient client;
    private final String base;

    public PlaybackQueueService() {
        this.base   = BackendAlexaClient.baseUrl();
        this.client = BackendAlexaClient.create();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /** Ritorna la prossima riga in coda (position minima) per l'utente. */
    public Optional<QueueItem> findNext(String userId) {
        QueueItem body = client.get()
                .uri(base + "/queue/next?email=" + enc(userId))
                .retrieve()
                .body(QueueItem.class); // null su 204 No Content
        return Optional.ofNullable(body);
    }

    /**
     * Conta le righe in coda per l'utente. Usato dal flusso auto-refill
     * (caso 15) per decidere se rifornire la coda.
     */
    public int countByUserId(String userId) {
        Map<?, ?> body = client.get()
                .uri(base + "/queue/count?email=" + enc(userId))
                .retrieve()
                .body(Map.class);

        if (body == null) return 0;
        Object count = body.get("count");
        return count instanceof Number ? ((Number) count).intValue() : 0;
    }

    /** Cancella la riga (user_id, position) dalla coda. */
    public void delete(String userId, int position) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", userId);
        payload.put("position", position);

        client.post()
                .uri(base + "/queue/delete")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }
}
