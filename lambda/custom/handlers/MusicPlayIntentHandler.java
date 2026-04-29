package handlers;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.impl.IntentRequestHandler;
import com.amazon.ask.model.IntentRequest;
import com.amazon.ask.model.Response;
import com.amazon.ask.model.interfaces.audioplayer.PlayBehavior;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.springframework.http.*;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;
import util.CurrentTrack;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.slf4j.LoggerFactory.getLogger;

public class MusicPlayIntentHandler implements IntentRequestHandler {

    private static Logger LOG = getLogger(GenericExceptionHandler.class);


    @Override
    public boolean canHandle(HandlerInput handlerInput, IntentRequest intentRequest) {
        return intentRequest.getIntent().getName().equals("MusicPlayIntent");
    }

    @Override
    public Optional<Response> handle(HandlerInput handlerInput, IntentRequest intentRequest) {
        //final String repromptText = IspUtil.getRandomObject(SkillData.YES_NO_STRINGS);
        //final String speechText = String.format("%s %s", IspUtil.getRandomObject(SkillData.MUSIC_PLAY_STRINGS), repromptText);
        //final String speechText = String.format("%s", IspUtil.getRandomObject(SkillData.MUSIC_PLAY_STRINGS));

        String token = "bbcpersian";

        // Recupera il token dall'account linking Alexa
        String accessToken = handlerInput.getRequestEnvelope().getContext().getSystem().getUser().getAccessToken();
        if (accessToken == null) {
            return handlerInput.getResponseBuilder()
                    .withSpeech("Per usare ebeat devi collegare il tuo account. Controlla l'app Alexa.")
                    .withLinkAccountCard()
                    .withShouldEndSession(true)
                    .build();
        }

        RestTemplate restTemplate = new RestTemplate();
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(new ObjectMapper());
        restTemplate.getMessageConverters().add(converter);

        String bearerToken = System.getenv("SUPABASE_SERVICE_KEY");
        String supabaseBase = System.getenv("SUPABASE_DB_TRACK_URI"); // es. https://<ref>.supabase.co/rest/v1/current_track

        // Risolve UUID → email chiamando l'API admin di Supabase Auth
        String username;
        String name;
        try {
            String adminUrl = supabaseBase.replaceAll("/rest/v1/.*$", "") + "/auth/v1/admin/users/" + accessToken;
            HttpHeaders adminHeaders = new HttpHeaders();
            adminHeaders.add("apikey", bearerToken);
            adminHeaders.add("Authorization", "Bearer " + bearerToken);
            ResponseEntity<Map> userResponse = restTemplate.exchange(
                    adminUrl, HttpMethod.GET, new HttpEntity<>(adminHeaders), Map.class);
            username = (String) userResponse.getBody().get("email");
            name = (String) userResponse.getBody().get("display_name");
            LOG.info("Username risolto: {}", username);
            LOG.info("Nome risolto: {}", name);
        } catch (Exception e) {
            LOG.error("Errore risoluzione utente [{}: {}]", e.getClass().getSimpleName(), e.getMessage());
            return handlerInput.getResponseBuilder()
                    .withSpeech("Errore nel riconoscere il tuo account. Riprova.")
                    .withShouldEndSession(true)
                    .build();
        }

        String DB_URI = supabaseBase + "?user_id=eq." + username;

        HttpHeaders headers = new HttpHeaders();
        headers.add("apikey", bearerToken);
        headers.add("Authorization", "Bearer " + bearerToken);
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));

        HttpEntity requestEntity = new HttpEntity(headers);

        ResponseEntity<CurrentTrack[]> response = restTemplate.exchange(DB_URI, HttpMethod.GET, requestEntity, CurrentTrack[].class);
        List<CurrentTrack> tracks = Arrays.asList(response.getBody());

        LOG.info("Righe restituite da Supabase: {}", tracks.size());

        if (tracks.isEmpty()) {
            return handlerInput.getResponseBuilder()
                    .withSpeech("Nessuna traccia trovata su ebeat. Avvia la riproduzione dall'app.")
                    .withShouldEndSession(true)
                    .build();
        }

        CurrentTrack current = tracks.get(0);

        // Controlla se l'URL YouTube è ancora valido
        Timestamp now = new Timestamp(System.currentTimeMillis());
        if (current.getUrl_expires_at() != null && current.getUrl_expires_at().before(now)) {
            return handlerInput.getResponseBuilder()
                    .withSpeech("Il link della traccia è scaduto. Riapri l'app ebeat per aggiornarlo.")
                    .withShouldEndSession(true)
                    .build();
        }

        String url = current.getUrl();
        Long offset = current.getOffset() != null ? current.getOffset() : 0L;
        String trackTitle = current.getTrack_title() != null ? current.getTrack_title() : "la tua musica";
        String trackArtist = current.getTrack_artist() != null ? current.getTrack_artist() : "";

        String speech = trackArtist.isEmpty()
                ? "Riproduco " + trackTitle + " da ebeat."
                : "Riproduco " + trackTitle + " di " + trackArtist + " da ebeat.";

        return handlerInput.getResponseBuilder()
                .withSpeech(speech)
                .addAudioPlayerPlayDirective(PlayBehavior.REPLACE_ALL, offset, "", token, url)
                .withShouldEndSession(true)
                .build();
       /*
        return handlerInput.getResponseBuilder()
                .withSpeech(speechText)
                //.withReprompt(repromptText)
                .build();

        */
    }

}
