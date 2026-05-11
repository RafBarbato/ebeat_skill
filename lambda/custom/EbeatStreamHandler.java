import com.amazon.ask.Skill;
import com.amazon.ask.Skills;
import com.amazon.ask.SkillStreamHandler;
import handlers.*;

public class EbeatStreamHandler extends SkillStreamHandler {

    private static Skill getSkill() {
        return Skills.standard()
                .addRequestHandlers(
                        new LaunchHandler(),
                        new PlayTrackIntentHandler(),
                        new YesIntentHandler(),
                        new NoIntentHandler(),
                        new MusicPlayIntentHandler(),
                        new PauseIntentHandler(),
                        new NextIntentHandler(),
                        new CancelAndStopIntentHandler(),
                        new PlaybackStartedHandler(),
                        new PlaybackStoppedHandler(),
                        new PlaybackNearlyFinishedHandler(),
                        new PlaybackFailedHandler(),
                        new PlaybackEventLogHandler(),
                        new HelpIntentHandler(),
                        new FallbackIntentHandler(),
                        new SessionEndedHandler(),
                        new UnknownRequestLogHandler())
                .addExceptionHandlers(new GenericExceptionHandler())
                .build();
    }

    public EbeatStreamHandler() {
        super(getSkill());
    }

}
