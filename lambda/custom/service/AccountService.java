package service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

public class AccountService {

    private final RestTemplate restTemplate;
    private final String supabaseAuthBase;
    private final String serviceKey;

    public AccountService() {
        this.restTemplate = buildRestTemplate();
        this.serviceKey = System.getenv("SUPABASE_SERVICE_KEY");

        String trackUri = System.getenv("SUPABASE_DB_TRACK_URI");
        this.supabaseAuthBase = trackUri.replaceAll("/rest/v1/.*$", "") + "/auth/v1/admin/users/";
    }

    /**
     * Risolve l'access token (UUID emesso dal nostro OAuth server) chiamando
     * l'API admin di Supabase Auth per ottenere l'email dell'utente.
     */
    public String resolveEmail(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("apikey", serviceKey);
        headers.add("Authorization", "Bearer " + serviceKey);

        ResponseEntity<Map> response = restTemplate.exchange(
                supabaseAuthBase + accessToken,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class
        );

        Object email = response.getBody().get("email");
        if (email == null) {
            throw new IllegalStateException("Campo 'email' mancante nella risposta admin Supabase");
        }
        return (String) email;
    }

    private static RestTemplate buildRestTemplate() {
        RestTemplate rt = new RestTemplate();
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(new ObjectMapper());
        rt.getMessageConverters().add(converter);
        return rt;
    }
}
