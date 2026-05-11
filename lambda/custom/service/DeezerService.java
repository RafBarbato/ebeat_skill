package service;

import org.springframework.web.client.RestClient;
import util.DeezerTrack;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Wrapper sull'API pubblica Deezer (api.deezer.com/search/track).
 * Nessun auth richiesto. Usata da {@link handlers.PlayTrackIntentHandler}
 * per il turn 1 della ricerca vocale (caso 15): ottiene metadati canonici
 * (titolo/artista/durata) da proporre all'utente prima di risolvere il
 * youtube_id via BE.
 */
public class DeezerService {

    private static final String BASE = "https://api.deezer.com/search/track";

    private final RestClient client;

    public DeezerService() {
        this.client = RestClient.builder().build();
    }

    /**
     * Cerca su Deezer e ritorna il primo hit. {@link Optional#empty()} se
     * nessun match o se la query è troppo corta (Deezer rifiuta &lt;3 char).
     */
    public Optional<DeezerTrack> firstMatch(String query) {
        if (query == null || query.trim().length() < 3) return Optional.empty();
        String encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = client.get()
                .uri(BASE + "?q=" + encoded + "&limit=5")
                .retrieve()
                .body(Map.class);

        if (body == null) return Optional.empty();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) body.get("data");
        if (data == null || data.isEmpty()) return Optional.empty();

        Map<String, Object> hit = data.get(0);
        DeezerTrack t = new DeezerTrack();

        Object id = hit.get("id");
        if (id instanceof Number) t.setId(((Number) id).longValue());

        Object title = hit.get("title");
        if (title != null) t.setTitle(title.toString());

        Object dur = hit.get("duration");
        if (dur instanceof Number) t.setDuration(((Number) dur).longValue());

        @SuppressWarnings("unchecked")
        Map<String, Object> artist = (Map<String, Object>) hit.get("artist");
        if (artist != null && artist.get("name") != null) {
            t.setArtist(artist.get("name").toString());
        }

        return Optional.of(t);
    }
}
