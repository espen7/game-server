package game.engine.portal;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.apache.pekko.routing.FromConfig;
import org.apache.pekko.testkit.javadsl.TestKit;
import game.engine.portal.actor.PortalActor;
import game.engine.core.message.Envelope;
import game.engine.core.message.Letter;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;

public class PortalActorTest {
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
        new TestKit(system) {
            {
                final ActorRef portalRouter = system.actorOf(
                        FromConfig.getInstance().props(Props.create(PortalActor.class)),
                        "portalRouter");

                Letter letter = new Letter(1, "valid_token".getBytes());
                Envelope envelope = new Envelope(letter, 1L, System.currentTimeMillis());

                portalRouter.tell(envelope, getRef());

                expectMsg(Duration.ofSeconds(3), "AUTH_SUCCESS");
            }
        };
    }

    @Test
    public void testAuthFailure() {
        new TestKit(system) {
            {
                final ActorRef portalRouter = system.actorOf(
                        FromConfig.getInstance().props(Props.create(PortalActor.class)),
                        "portalRouterFailure");

                Letter letter = new Letter(1, new byte[0]); // Invalid token
                Envelope envelope = new Envelope(letter, 2L, System.currentTimeMillis());

                portalRouter.tell(envelope, getRef());

                expectMsg(Duration.ofSeconds(3), "AUTH_FAILURE");
            }
        };
    }
}
