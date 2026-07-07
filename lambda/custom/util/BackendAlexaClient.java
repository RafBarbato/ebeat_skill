package util;

import org.springframework.web.client.RestClient;

/**
 * Factory per il RestClient verso gli endpoint interni del BE
 * ({@code /v1/internal/alexa/*}), autenticato con il bearer condiviso
 * {@code SKILL_BE_SECRET}.
 *
 * Sostituisce l'accesso diretto della skill a Supabase (Fase 3 della
 * migrazione Redis): la skill non parla più al DB, ma al BE, che possiede
 * lo store (Supabase o Redis, dietro feature-flag lato BE).
 *
 * Env richieste:
 *  - {@code SKILL_BE_INTERNAL_URL} : base URL, es. {@code https://host/v1/internal/alexa}
 *  - {@code SKILL_BE_SECRET}       : bearer condiviso col BE
 */
public final class BackendAlexaClient {

    private BackendAlexaClient() {}

    public static RestClient create() {
        String secret = System.getenv("SKILL_BE_SECRET");
        return RestClient.builder()
                .defaultHeader("Authorization", "Bearer " + secret)
                .build();
    }

    /** Base URL degli endpoint interni Alexa del BE (senza slash finale). */
    public static String baseUrl() {
        String url = System.getenv("SKILL_BE_INTERNAL_URL");
        if (url == null) return null;
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
