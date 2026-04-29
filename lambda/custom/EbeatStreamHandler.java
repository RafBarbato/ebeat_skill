import com.amazon.ask.Skill;
import com.amazon.ask.Skills;
import com.amazon.ask.SkillStreamHandler;
import handlers.*;

public class EbeatStreamHandler extends SkillStreamHandler {

    private static Skill getSkill() {
        return Skills.standard()
                .addRequestHandlers(
                        new LaunchHandler(),
                        new MusicPlayIntentHandler(),
                        new CancelAndStopIntentHandler(),
                        new HelpIntentHandler(),
                        new FallbackIntentHandler(),
                        new SessionEndedHandler())
                .addExceptionHandlers(new GenericExceptionHandler())
                .build();
    }

    public EbeatStreamHandler() {
        super(getSkill());
    }

}
