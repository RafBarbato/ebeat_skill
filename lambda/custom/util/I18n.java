package util;

import java.util.HashMap;
import java.util.Map;

/**
 * Localizzazione delle risposte vocali della skill. Selezione per {@code locale}
 * della richiesta (es. "it-IT", "en-US"); fallback italiano. Placeholder in
 * stile {@link String#format} ({@code %s}).
 *
 * Implementazione a mappe Java (niente ResourceBundle): robusta nel jar shaded
 * della Lambda, nessun caricamento di risorse a runtime. Per aggiungere una
 * lingua: estendere le coppie con un terzo elemento e la logica in {@link #pick}.
 */
public final class I18n {

    private I18n() {}

    // key -> [it, en]
    private static final Map<String, String[]> M = new HashMap<>();

    static {
        // --- PlaybackStarter / avvio riproduzione ---
        put("link_account_card",
                "Per usare ebeat devi collegare il tuo account. Controlla l'app Alexa.",
                "To use ebeat you need to link your account. Check the Alexa app.");
        put("account_error",
                "Errore nel riconoscere il tuo account. Riprova.",
                "I couldn't recognize your account. Please try again.");
        put("be_unreachable",
                "Al momento non riesco a raggiungere ebeat. Riprova tra poco.",
                "I can't reach ebeat right now. Please try again shortly.");
        put("no_track",
                "Nessuna traccia trovata su ebeat. Avvia la riproduzione dall'app.",
                "No track found on ebeat. Start playback from the app.");
        put("track_stale",
                "La traccia non è aggiornata. Apri l'app ebeat per aggiornarla, poi riprova.",
                "This track is out of date. Open the ebeat app to refresh it, then try again.");
        put("default_title", "la tua musica", "your music");
        put("play_loop", "Riproduco %s in loop.", "Playing %s on loop.");
        put("play_startover", "Riavvio %s da capo.", "Restarting %s from the beginning.");
        put("resume", "Riprendo.", "Resuming.");
        put("sync", "Aggiorno.", "Updating.");
        put("play_title", "Riproduco %s da ebeat.", "Playing %s from ebeat.");
        put("play_title_artist", "Riproduco %s di %s da ebeat.", "Playing %s by %s from ebeat.");

        // --- Stop / Cancel ---
        put("goodbye", "A presto!", "See you soon!");

        // --- Fallback ---
        put("fallback_speech",
                "Non ho capito. Prova a dire play per ascoltare la tua musica, oppure aiuto.",
                "I didn't get that. Try saying play to listen to your music, or help.");
        put("fallback_reprompt",
                "Dì play per avviare la riproduzione.",
                "Say play to start playback.");

        // --- Errore generico ---
        put("generic_error",
                "Si è verificato un problema con ebeat. Riprova tra poco.",
                "Something went wrong with ebeat. Please try again shortly.");
        put("generic_error_short",
                "Si è verificato un errore.",
                "Something went wrong.");

        // --- Help ---
        put("help_speech",
                "Con ebeat puoi riprendere l'ascolto dall'app. Dì play per avviare la riproduzione, oppure stop per fermarla.",
                "With ebeat you can resume listening from the app. Say play to start, or stop to stop.");
        put("help_reprompt",
                "Cosa vuoi fare? Dì play per ascoltare la tua musica.",
                "What would you like to do? Say play to listen to your music.");

        // --- Loop off ---
        put("loop_off", "Loop disattivato.", "Loop turned off.");

        // --- Next / coda ---
        put("link_account_short",
                "Per usare ebeat devi collegare il tuo account.",
                "To use ebeat you need to link your account.");
        put("queue_empty",
                "Non ci sono altre tracce in coda.",
                "There are no more tracks in the queue.");
        put("next_not_ready",
                "La prossima traccia non è ancora pronta. Apri l'app ebeat per aggiornare la coda.",
                "The next track isn't ready yet. Open the ebeat app to refresh the queue.");
        put("next_failed",
                "Non sono riuscito a passare alla prossima.",
                "I couldn't skip to the next track.");
        put("track_stale_short",
                "La traccia non è aggiornata. Apri l'app ebeat per aggiornarla.",
                "This track is out of date. Open the ebeat app to refresh it.");

        // --- Controlli non supportati ---
        put("not_supported",
                "Questa funzione non è disponibile su ebeat.",
                "This feature isn't available on ebeat.");
    }

    private static void put(String key, String it, String en) {
        M.put(key, new String[]{it, en});
    }

    private static boolean isEnglish(String locale) {
        return locale != null && locale.toLowerCase().startsWith("en");
    }

    private static String pick(String[] pair, String locale) {
        return isEnglish(locale) ? pair[1] : pair[0];
    }

    /** Messaggio localizzato per la lingua della richiesta. */
    public static String t(String locale, String key, Object... args) {
        String[] pair = M.get(key);
        if (pair == null) return key; // chiave mancante: mai un crash
        String tmpl = pick(pair, locale);
        return args.length == 0 ? tmpl : String.format(tmpl, args);
    }
}
