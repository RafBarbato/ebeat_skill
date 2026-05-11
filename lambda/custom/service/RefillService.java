package service;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

/**
 * Richiede al BE Node di rifornire {@code playback_queue} con tracce
 * correlate al seed (Deezer Radio + youtubei.js resolve in parallelo +
 * INSERT). Caso 15 — auto-refill radio infinita.
 *
 * Endpoint BE: {@code POST /refill-queue}, auth Bearer service key
 * (stessa convenzione di {@link RefreshService}).
 */
public class RefillService {

    private final RestClient client;
    private final String refillUrl;

    public RefillService() {
        String serviceKey = System.getenv("SUPABASE_SERVICE_KEY");
        this.refillUrl    = System.getenv("BACKEND_REFILL_URL");

        this.client = RestClient.builder()
                .defaultHeader("Authorization", "Bearer " + serviceKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * No-op se seed null o endpoint non configurato. Eccezioni di rete
     * propagate al chiamante (gli handler le gestiscono come best-effort).
     */
    public void refill(String userId, Long seedDeezerId) {
        if (seedDeezerId == null || refillUrl == null) return;

        Map<String, Object> body = new HashMap<>();
        body.put("user_id", userId);
        body.put("seed_deezer_id", seedDeezerId);

        client.post()
                .uri(refillUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }
}
