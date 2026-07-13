package service;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import util.BackendAlexaClient;

import java.util.HashMap;
import java.util.Map;

/**
 * Risolve l'access token OAuth (token opaco emesso dal nostro OAuth server)
 * nell'email dell'utente tramite il BE ({@code POST /internal/alexa/resolve}).
 *
 * Fase 5 migrazione Redis: la skill non chiama più direttamente l'admin di
 * Supabase. Il BE valida il token (esistenza + scadenza) e ritorna l'email —
 * così l'access token è un vero segreto a scadenza, non lo UUID utente in chiaro.
 */
public class AccountService {

    private final RestClient client;
    private final String base;

    public AccountService() {
        this.base   = BackendAlexaClient.baseUrl();
        this.client = BackendAlexaClient.create();
    }

    /** Ritorna l'email associata all'access token, o lancia se invalido/scaduto. */
    public String resolveEmail(String accessToken) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("accessToken", accessToken);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = client.post()
                .uri(base + "/resolve")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()               // 401 su token invalido/scaduto -> eccezione
                .body(Map.class);

        if (body == null || body.get("email") == null) {
            throw new IllegalStateException("Email non risolta dal BE (token invalido o scaduto)");
        }
        return (String) body.get("email");
    }
}
