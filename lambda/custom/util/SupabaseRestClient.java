package util;

import org.springframework.web.client.RestClient;

/** Factory per RestClient pre-configurato con gli header Supabase. */
public final class SupabaseRestClient {

    private SupabaseRestClient() {}

    public static RestClient create() {
        String key = System.getenv("SUPABASE_SERVICE_KEY");
        return RestClient.builder()
                .defaultHeader("apikey", key)
                .defaultHeader("Authorization", "Bearer " + key)
                .build();
    }
}
