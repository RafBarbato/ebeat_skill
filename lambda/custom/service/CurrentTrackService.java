package service;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import util.CurrentTrack;
import util.DeezerTrack;
import util.QueueItem;
import util.SupabaseRestClient;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class CurrentTrackService {

    private final RestClient client;
    private final String trackUri;

    public CurrentTrackService() {
        this.trackUri = System.getenv("SUPABASE_DB_TRACK_URI");
        this.client   = SupabaseRestClient.create();
    }

    /** Recupera la traccia corrente associata all'utente (filtra per user_id = email). */
    public Optional<CurrentTrack> findByUserId(String userId) {
        CurrentTrack[] body = client.get()
                .uri(trackUri + "?user_id=eq." + userId)
                .retrieve()
                .body(CurrentTrack[].class);

        if (body == null || body.length == 0) {
            return Optional.empty();
        }
        return Optional.of(body[0]);
    }

    /** Aggiorna solo l'offset (e updated_at) per l'utente. */
    public void updateOffset(String userId, long offsetMs) {
        Map<String, Object> body = new HashMap<>();
        body.put("offset", offsetMs);
        body.put("updated_at", Instant.now().toString());

        client.patch()
                .uri(trackUri + "?user_id=eq." + userId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Aggiorna lo stato di riproduzione su current_track (casi 11/12/14):
     * device attivo (es. "alexa:&lt;deviceId&gt;", o null) e flag is_playing.
     * Serve all'app per riflettere cosa/come sta suonando Alexa (Realtime).
     */
    public void setPlaybackState(String userId, String activeDevice, boolean isPlaying) {
        Map<String, Object> body = new HashMap<>();
        body.put("active_device", activeDevice);
        body.put("is_playing", isPlaying);
        body.put("playback_state_changed_at", Instant.now().toString());
        body.put("updated_at", Instant.now().toString());

        client.patch()
                .uri(trackUri + "?user_id=eq." + userId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    /** Aggiorna solo il flag is_playing (mantiene active_device). Pausa/stop. */
    public void setIsPlaying(String userId, boolean isPlaying) {
        Map<String, Object> body = new HashMap<>();
        body.put("is_playing", isPlaying);
        body.put("playback_state_changed_at", Instant.now().toString());
        body.put("updated_at", Instant.now().toString());

        client.patch()
                .uri(trackUri + "?user_id=eq." + userId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    /** Attiva o disattiva la modalità loop per l'utente. */
    public void setLoopMode(String userId, boolean enabled) {
        Map<String, Object> body = new HashMap<>();
        body.put("loop_mode", enabled);
        body.put("updated_at", Instant.now().toString());

        client.patch()
                .uri(trackUri + "?user_id=eq." + userId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Promuove una riga di playback_queue come traccia corrente.
     * Aggiorna current_track con i metadati dalla coda, azzera offset
     * e aggiorna updated_at. Caso 7 (PlaybackNearlyFinished -> next).
     */
    public void promoteFromQueue(String userId, QueueItem item) {
        Map<String, Object> body = new HashMap<>();
        body.put("offset", 0L);
        body.put("youtube_id", item.getYoutube_id());
        body.put("url", item.getUrl());
        body.put("url_expires_at",
                item.getUrl_expires_at() != null
                        ? item.getUrl_expires_at().toInstant().toString()
                        : null);
        body.put("track_id", item.getTrack_id());
        body.put("track_title", item.getTrack_title());
        body.put("track_artist", item.getTrack_artist());
        body.put("track_duration", item.getTrack_duration());
        body.put("updated_at", Instant.now().toString());

        client.patch()
                .uri(trackUri + "?user_id=eq." + userId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Promuove come traccia corrente un risultato di ricerca Deezer abbinato
     * a un youtube_id risolto via BE. Metadati dalla risposta Deezer
     * (canonici), url azzerato: sarà {@link RefreshService} a popolarlo
     * subito dopo. Caso 15 (ricerca vocale).
     */
    public void promoteFromSearch(String userId, DeezerTrack t, String youtubeId) {
        Map<String, Object> body = new HashMap<>();
        body.put("offset", 0L);
        body.put("youtube_id", youtubeId);
        body.put("url", null);
        body.put("url_expires_at", null);
        body.put("track_id", t.getId());
        body.put("track_title", t.getTitle());
        body.put("track_artist", t.getArtist());
        body.put("track_duration", t.getDuration());
        // Pin del seed Deezer per auto-refill radio (caso 15).
        body.put("radio_seed_track_id", t.getId());
        body.put("updated_at", Instant.now().toString());

        client.patch()
                .uri(trackUri + "?user_id=eq." + userId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }
}
