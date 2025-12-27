package game.engine.auth;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.apache.pekko.routing.FromConfig;
import org.apache.pekko.testkit.javadsl.TestKit;
import game.engine.auth.actor.AuthActor;
import game.engine.core.message.Envelope;
import game.engine.core.message.Letter;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;

public class AuthActorTest {
    private static ActorSystem system;

    @BeforeAll
    public static void setup() {
        Config config = ConfigFactory.load();
        system = ActorSystem.create("TestSystem", config);
    }

    @AfterAll
    public static void teardown() {
        TestKit.shutdownActorSystem(system);
        system = null;
    }

    @Test
    public void testAuthSuccess() {
        new TestKit(system) {{
            final ActorRef authRouter = system.actorOf(
                FromConfig.getInstance().props(Props.create(AuthActor.class)), 
                "authRouter"
            );

            Letter letter = new Letter(1, "valid_token".getBytes());
            Envelope envelope = new Envelope(letter, "player1", "gateway1");

            authRouter.tell(envelope, getRef());

            expectMsg(Duration.ofSeconds(3), "AUTH_SUCCESS");
        }};
    }

    @Test
    public void testAuthFailure() {
        new TestKit(system) {{
            final ActorRef authRouter = system.actorOf(
                FromConfig.getInstance().props(Props.create(AuthActor.class)), 
                "authRouterFailure"
            );

            Letter letter = new Letter(1, new byte[0]); // Invalid token
            Envelope envelope = new Envelope(letter, "player2", "gateway1");

            authRouter.tell(envelope, getRef());

            expectMsg(Duration.ofSeconds(3), "AUTH_FAILURE");
        }};
    }
}
