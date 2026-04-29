package service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

public class RefreshService {

    private final RestTemplate restTemplate;
    private final String refreshUrl;
    private final String serviceKey;

    public RefreshService() {
        this.restTemplate = buildRestTemplate();
        this.refreshUrl = System.getenv("BACKEND_REFRESH_URL");
        this.serviceKey = System.getenv("SUPABASE_SERVICE_KEY");
    }

    /**
     * Richiede al backend OAuth di rigenerare l'URL stream YouTube per la traccia
     * corrente dell'utente. Il backend aggiorna direttamente Supabase.
     */
    public void refresh(String userId, String youtubeId) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + serviceKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = new HashMap<>();
        body.put("user_id", userId);
        body.put("youtube_id", youtubeId);

        restTemplate.postForObject(refreshUrl, new HttpEntity<>(body, headers), Map.class);
    }

    private static RestTemplate buildRestTemplate() {
        RestTemplate rt = new RestTemplate();
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(new ObjectMapper());
        rt.getMessageConverters().add(converter);
        return rt;
    }
}
