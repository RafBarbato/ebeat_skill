package service;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Richiede al BE Node di mappare (title, artist, duration) → youtube_id
 * usando youtubei.js + scoring per durata. Caso 15 — turn 2 della
 * ricerca vocale, dopo conferma utente.
 *
 * Endpoint BE: {@code POST /resolve-youtube-id}, auth Bearer service key
 * (stessa convenzione di {@link RefreshService}).
 */
public class YoutubeResolveService {

    private final RestClient client;
    private final String resolveUrl;

    public YoutubeResolveService() {
        String serviceKey = System.getenv("SUPABASE_SERVICE_KEY");
        this.resolveUrl   = System.getenv("BACKEND_RESOLVE_URL");

        this.client = RestClient.builder()
                .defaultHeader("Authorization", "Bearer " + serviceKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Ritorna il youtube_id (11 char) del candidato YouTube migliore.
     * {@link Optional#empty()} se il BE non trova nessun match utile.
     */
    public Optional<String> resolveYoutubeId(String title, String artist, Long duration) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        if (artist != null) body.put("artist", artist);
        if (duration != null) body.put("duration", duration);

        @SuppressWarnings("unchecked")
        Map<String, Object> resp = client.post()
                .uri(resolveUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

        if (resp == null || resp.get("youtube_id") == null) return Optional.empty();
        String id = resp.get("youtube_id").toString();
        return id.isEmpty() ? Optional.empty() : Optional.of(id);
    }
}
