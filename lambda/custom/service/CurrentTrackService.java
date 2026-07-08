package service;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import util.BackendAlexaClient;
import util.CurrentTrack;
import util.DeezerTrack;
import util.QueueItem;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Accesso a {@code current_track} tramite gli endpoint interni del BE
 * ({@code /v1/internal/alexa/current-track}). La skill non parla più a Supabase
 * (Fase 3 migrazione Redis): il BE possiede lo store e riflette i cambi all'app
 * via SSE. Le firme pubbliche sono invariate, così gli handler non cambiano.
 */
public class CurrentTrackService {

    private final RestClient client;
    private final String base;

    public CurrentTrackService() {
        this.base   = BackendAlexaClient.baseUrl();
        this.client = BackendAlexaClient.create();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /** Applica una patch parziale a current_track ({@code {email, patch}}). */
    private void patch(String userId, Map<String, Object> patch) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", userId);
        payload.put("patch", patch);

        client.post()
                .uri(base + "/current-track/patch")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }

    /** Recupera la traccia corrente associata all'utente. */
    public Optional<CurrentTrack> findByUserId(String userId) {
        // URI (non String): RestClient tratterebbe la String come template e
        // ri-codificherebbe il %40 già prodotto da enc() → doppia codifica.
        CurrentTrack body = client.get()
                .uri(URI.create(base + "/current-track?email=" + enc(userId)))
                .retrieve()
                .body(CurrentTrack.class); // null su 204 No Content
        return Optional.ofNullable(body);
    }

    /** Aggiorna solo l'offset per l'utente. */
    public void updateOffset(String userId, long offsetMs) {
        Map<String, Object> p = new HashMap<>();
        p.put("offset", offsetMs);
        patch(userId, p);
    }

    /**
     * Aggiorna lo stato di riproduzione (casi 11/12/14): device attivo
     * (es. "alexa:&lt;deviceId&gt;", o null) e flag is_playing.
     */
    public void setPlaybackState(String userId, String activeDevice, boolean isPlaying) {
        Map<String, Object> p = new HashMap<>();
        p.put("active_device", activeDevice);
        p.put("is_playing", isPlaying);
        patch(userId, p);
    }

    /** Aggiorna solo il flag is_playing (mantiene active_device). Pausa/stop. */
    public void setIsPlaying(String userId, boolean isPlaying) {
        Map<String, Object> p = new HashMap<>();
        p.put("is_playing", isPlaying);
        patch(userId, p);
    }

    /** Attiva o disattiva la modalità loop per l'utente. */
    public void setLoopMode(String userId, boolean enabled) {
        Map<String, Object> p = new HashMap<>();
        p.put("loop_mode", enabled);
        patch(userId, p);
    }

    /**
     * Promuove una riga di playback_queue come traccia corrente (caso 7).
     * Metadati dalla coda, offset azzerato.
     */
    public void promoteFromQueue(String userId, QueueItem item) {
        Map<String, Object> p = new HashMap<>();
        p.put("offset", 0L);
        p.put("youtube_id", item.getYoutube_id());
        p.put("url", item.getUrl());
        p.put("url_expires_at",
                item.getUrl_expires_at() != null
                        ? item.getUrl_expires_at().toInstant().toString()
                        : null);
        p.put("track_id", item.getTrack_id());
        p.put("track_title", item.getTrack_title());
        p.put("track_artist", item.getTrack_artist());
        p.put("track_duration", item.getTrack_duration());
        patch(userId, p);
    }

    /**
     * Promuove come traccia corrente un risultato di ricerca Deezer abbinato a un
     * youtube_id risolto via BE (caso 15). url azzerato: sarà popolato subito
     * dopo. Pin del seed Deezer per auto-refill radio.
     */
    public void promoteFromSearch(String userId, DeezerTrack t, String youtubeId) {
        Map<String, Object> p = new HashMap<>();
        p.put("offset", 0L);
        p.put("youtube_id", youtubeId);
        p.put("url", null);
        p.put("url_expires_at", null);
        p.put("track_id", t.getId());
        p.put("track_title", t.getTitle());
        p.put("track_artist", t.getArtist());
        p.put("track_duration", t.getDuration());
        p.put("radio_seed_track_id", t.getId());
        patch(userId, p);
    }
}
