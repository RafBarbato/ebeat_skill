package service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;
import util.CurrentTrack;

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
        RestTemplate rt = new RestTemplate();
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(new ObjectMapper());
        rt.getMessageConverters().add(converter);
        return rt;
    }
}
