package service;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import util.SupabaseRestClient;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Registra/aggiorna il device Alexa corrente nella tabella {@code alexa_device}
 * così l'app puo elencarlo nel menu "dispositivi" (punto #2 — device di rete).
 *
 * Upsert su (user_id, device_id) con Prefer: resolution=merge-duplicates:
 * aggiorna solo {@code last_seen_at} e NON tocca {@code name} (assegnato
 * dall'utente lato app). {@code user_id} = email, come le altre tabelle Alexa.
 */
public class DeviceService {

    private final RestClient client;
    private final String deviceUri;

    public DeviceService() {
        String trackUri = System.getenv("SUPABASE_DB_TRACK_URI");
        // trackUri punta a .../rest/v1/current_track: sostituisco l'ultimo
        // segmento con alexa_device per riusare lo stesso base + service key.
        this.deviceUri = trackUri.substring(0, trackUri.lastIndexOf('/')) + "/alexa_device";
        this.client = SupabaseRestClient.create();
    }

    /** Upsert best-effort del device per l'utente. No-op se mancano i parametri. */
    public void registerDevice(String userId, String deviceId) {
        if (userId == null || deviceId == null || deviceId.isBlank()) {
            return;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("user_id", userId);
        body.put("device_id", deviceId);
        body.put("last_seen_at", Instant.now().toString());

        client.post()
                .uri(deviceUri)
                .header("Prefer", "resolution=merge-duplicates")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }
}
