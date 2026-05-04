package service;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

public class RefreshService {

    private final RestClient client;
    private final String refreshUrl;

    public RefreshService() {
        String serviceKey = System.getenv("SUPABASE_SERVICE_KEY");
        this.refreshUrl   = System.getenv("BACKEND_REFRESH_URL");

        this.client = RestClient.builder()
                .defaultHeader("Authorization", "Bearer " + serviceKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Richiede al backend Node di rigenerare l'URL stream YouTube per la
     * traccia corrente dell'utente. Il backend aggiorna direttamente Supabase.
     */
    public void refresh(String userId, String youtubeId) {
        Map<String, String> body = new HashMap<>();
        body.put("user_id", userId);
        body.put("youtube_id", youtubeId);

        client.post()
                .uri(refreshUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }
}
