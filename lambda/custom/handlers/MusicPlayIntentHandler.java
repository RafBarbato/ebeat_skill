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
import util.Url;
import java.util.Arrays;
import java.util.List;
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

        //String token = handlerInput.getRequestEnvelope().getContext().getSystem().getApiAccessToken();
        String token = null;
        if (token == null) token = "bbcpersian";
        String music="https://rr2---sn-8vq54voxpu-o52e.googlevideo.com/videoplayback?expire=1701836301&ei=raFvZd20At3D6dsPlrqF2Ag&ip=93.148.96.43&id=o-ANpgJxH3zpkvYUrIn-UWdS_taLgKuzaxTr2M-uwRjrDl&itag=18&source=youtube&requiressl=yes&xpc=EgVo2aDSNQ%3D%3D&mh=vF&mm=31%2C29&mn=sn-8vq54voxpu-o52e%2Csn-8vq54voxpu-hm2r&ms=au%2Crdu&mv=m&mvi=2&pl=22&initcwndbps=2066250&spc=UWF9fz4IYAMNpeYsG-puhCGar1MM_WfhcWGCHNpcbg&vprv=1&svpuc=1&mime=video%2Fmp4&ns=u7V-RM2-vEoPKLbrNQ8px9oP&gir=yes&clen=364743759&ratebypass=yes&dur=4961.221&lmt=1701299796769610&mt=1701814134&fvip=2&fexp=24007246&c=WEB&txp=5538434&n=Ao7dfl7E2rhabtge&sparams=expire%2Cei%2Cip%2Cid%2Citag%2Csource%2Crequiressl%2Cxpc%2Cspc%2Cvprv%2Csvpuc%2Cmime%2Cns%2Cgir%2Cclen%2Cratebypass%2Cdur%2Clmt&sig=ANLwegAwRgIhALCA8wOk9frWN9fE04d24JF3NmriZt017RQuz0UDA_OAAiEA6QodOMhgRfNjfkuZAIQU-LroRB9c8rXuqpZXFdat5Fs%3D&lsparams=mh%2Cmm%2Cmn%2Cms%2Cmv%2Cmvi%2Cpl%2Cinitcwndbps&lsig=AM8Gb2swRQIhAPhuwtYje5M5SyNPgfwpDS7Axlos4UYNjip2FfG8paC1AiBAYJVqSa8gscnSkqDqEUDxoIHxyDLrOQaAU2MLoc2VGA%3D%3D";


        RestTemplate restTemplate = new RestTemplate();
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(new ObjectMapper());
        restTemplate.getMessageConverters().add(converter);

        String DB_URI = "https://xzcdfhaylwoqbfucgcau.supabase.co/rest/v1/urls?user_id=eq.raffaele";

        // mock, da prendere da supabase
        String bearerToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inh6Y2RmaGF5bHdvcWJmdWNnY2F1Iiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTY5MDExNzgyNiwiZXhwIjoyMDA1NjkzODI2fQ.XvubjHsPBS_c_KMNvy-kW6BJ_h0UnSXObhCxvhs2x2w";

        HttpHeaders headers = new HttpHeaders();
        headers.add("apikey", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inh6Y2RmaGF5bHdvcWJmdWNnY2F1Iiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTY5MDExNzgyNiwiZXhwIjoyMDA1NjkzODI2fQ.XvubjHsPBS_c_KMNvy-kW6BJ_h0UnSXObhCxvhs2x2w");
        headers.add("Authorization", "Bearer "+bearerToken);
        headers.add("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.99 Safari/537.36");
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));

        HttpEntity requestEntity = new HttpEntity(headers);

        ResponseEntity<Url[]> response = restTemplate.exchange(DB_URI, HttpMethod.GET, requestEntity, Url[].class);
        List<Url> urls =  Arrays.asList(response.getBody());

        String username = urls.get(0).getUser_id();

        String url = urls.get(0).getUrl();
        Long offset = urls.get(0).getOffset();

        return handlerInput.getResponseBuilder()
                .withSpeech("Riproduco musica da ebeat per " + username)
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
