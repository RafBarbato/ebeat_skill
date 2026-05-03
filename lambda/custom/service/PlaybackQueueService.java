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
import util.QueueItem;

import java.util.Arrays;
import java.util.Optional;

/**
 * Accesso REST PostgREST alla tabella playback_queue (caso 7).
 *
 * L'URL endpoint viene derivato da SUPABASE_DB_TRACK_URI sostituendo
 * "/current_track" con "/playback_queue", per non aggiungere una nuova
 * env var alla skill.
 */
public class PlaybackQueueService {

    private final RestTemplate restTemplate;
    private final String queueUri;
    private final String serviceKey;

    public PlaybackQueueService() {
        this.restTemplate = buildRestTemplate();
        String trackUri = System.getenv("SUPABASE_DB_TRACK_URI");
        this.queueUri = trackUri != null
                ? trackUri.replace("/current_track", "/playback_queue")
                : null;
        this.serviceKey = System.getenv("SUPABASE_SERVICE_KEY");
    }

    /** Ritorna la prossima riga in coda (position minima) per l'utente. */
    public Optional<QueueItem> findNext(String userId) {
        if (queueUri == null) return Optional.empty();
        HttpHeaders headers = new HttpHeaders();
        headers.add("apikey", serviceKey);
        headers.add("Authorization", "Bearer " + serviceKey);
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));

        ResponseEntity<QueueItem[]> response = restTemplate.exchange(
                queueUri + "?user_id=eq." + userId + "&order=position.asc&limit=1",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                QueueItem[].class
        );
        QueueItem[] body = response.getBody();
        if (body == null || body.length == 0) {
            return Optional.empty();
        }
        return Optional.of(body[0]);
    }

    /** Cancella la riga (user_id, position) dalla coda. */
    public void delete(String userId, int position) {
        if (queueUri == null) return;
        HttpHeaders headers = new HttpHeaders();
        headers.add("apikey", serviceKey);
        headers.add("Authorization", "Bearer " + serviceKey);

        restTemplate.exchange(
                queueUri + "?user_id=eq." + userId + "&position=eq." + position,
                HttpMethod.DELETE,
                new HttpEntity<>(headers),
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
