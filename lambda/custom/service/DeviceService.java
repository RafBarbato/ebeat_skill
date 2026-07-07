package service;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import util.BackendAlexaClient;

import java.util.HashMap;
import java.util.Map;

/**
 * Registra/aggiorna il device Alexa corrente via BE
 * ({@code /v1/internal/alexa/device/register}) così l'app puo elencarlo nel
 * menu "dispositivi". Upsert su (user_id, device_id): aggiorna solo
 * {@code last_seen_at}, NON tocca {@code name} (assegnato dall'utente lato app).
 * {@code user_id} = email. Fase 3 migrazione Redis (niente più Supabase).
 */
public class DeviceService {

    private final RestClient client;
    private final String base;

    public DeviceService() {
        this.base   = BackendAlexaClient.baseUrl();
        this.client = BackendAlexaClient.create();
    }

    /** Upsert best-effort del device per l'utente. No-op se mancano i parametri. */
    public void registerDevice(String userId, String deviceId) {
        if (userId == null || deviceId == null || deviceId.isBlank()) {
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("email", userId);
        payload.put("deviceId", deviceId);

        client.post()
                .uri(base + "/device/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }
}
