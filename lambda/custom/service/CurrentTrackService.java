package service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;
import util.CurrentTrack;
import util.QueueItem;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class CurrentTrackService {

    private final RestTemplate restTemplate;
    private final String trackUri;
    private final String serviceKey;

    public CurrentTrackService() {
        this.restTemplate = buildRestTemplate();
        this.trackUri = System.getenv("SUPABASE_DB_TRACK_URI");
        this.serviceKey = System.getenv("SUPABASE_SERVICE_KEY");
    }

    /**
     * Recupera la traccia corrente associata all'utente (filtra per user_id = email).
     */
    public Optional<CurrentTrack> findByUserId(String userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("apikey", serviceKey);
        headers.add("Authorization", "Bearer " + serviceKey);
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));

        ResponseEntity<CurrentTrack[]> response = restTemplate.exchange(
                trackUri + "?user_id=eq." + userId,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                CurrentTrack[].class
        );

        CurrentTrack[] body = response.getBody();
        if (body == null || body.length == 0) {
            return Optional.empty();
        }
        return Optional.of(body[0]);
    }

    /**
     * Aggiorna solo l'offset (e updated_at) per l'utente.
     */
    public void updateOffset(String userId, long offsetMs) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("apikey", serviceKey);
        headers.add("Authorization", "Bearer " + serviceKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("offset", offsetMs);
        body.put("updated_at", Instant.now().toString());

        restTemplate.exchange(
                trackUri + "?user_id=eq." + userId,
                HttpMethod.PATCH,
                new HttpEntity<>(body, headers),
                Void.class
        );
    }

    /**
     * Promuove una riga di playback_queue come traccia corrente.
     * Aggiorna current_track con i metadati dalla coda, azzera offset
     * e aggiorna updated_at. Caso 7 (PlaybackNearlyFinished -> next).
     */
    public void promoteFromQueue(String userId, QueueItem item) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("apikey", serviceKey);
        headers.add("Authorization", "Bearer " + serviceKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

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

        restTemplate.exchange(
                trackUri + "?user_id=eq." + userId,
                HttpMethod.PATCH,
                new HttpEntity<>(body, headers),
                Void.class
        );
    }

    /**
     * Attiva o disattiva la modalità loop per l'utente.
     */
    public void setLoopMode(String userId, boolean enabled) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("apikey", serviceKey);
        headers.add("Authorization", "Bearer " + serviceKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("loop_mode", enabled);
        body.put("updated_at", Instant.now().toString());

        restTemplate.exchange(
                trackUri + "?user_id=eq." + userId,
                HttpMethod.PATCH,
                new HttpEntity<>(body, headers),
                Void.class
        );
    }

    private static RestTemplate buildRestTemplate() {
        RestTemplate rt = new RestTemplate(new HttpComponentsClientHttpRequestFactory());
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(new ObjectMapper());
        rt.getMessageConverters().add(converter);
        return rt;
    }
}
