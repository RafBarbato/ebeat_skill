package service;

import org.springframework.web.client.RestClient;
import util.SupabaseRestClient;

import java.util.Map;

public class AccountService {

    private final RestClient client;
    private final String supabaseAuthBase;

    public AccountService() {
        String trackUri   = System.getenv("SUPABASE_DB_TRACK_URI");
        this.supabaseAuthBase = trackUri.replaceAll("/rest/v1/.*$", "") + "/auth/v1/admin/users/";
        this.client = SupabaseRestClient.create();
    }

    /**
     * Risolve l'access token (UUID emesso dal nostro OAuth server) chiamando
     * l'API admin di Supabase Auth per ottenere l'email dell'utente.
     */
    public String resolveEmail(String accessToken) {
        @SuppressWarnings("unchecked")
        Map<String, Object> body = client.get()
                .uri(supabaseAuthBase + accessToken)
                .retrieve()
                .body(Map.class);

        if (body == null || body.get("email") == null) {
            throw new IllegalStateException("Campo 'email' mancante nella risposta admin Supabase");
        }
        return (String) body.get("email");
    }
}
