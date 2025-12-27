package game.engine.auth;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.apache.pekko.routing.FromConfig;
import game.engine.auth.actor.AuthActor;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class AuthService {
    private final ActorSystem system;
    private final ActorRef authRouter;

    public AuthService() {
        Config config = ConfigFactory.load();
        this.system = ActorSystem.create("AuthSystem", config);
        
        // Create the auth router from configuration
        this.authRouter = system.actorOf(
            FromConfig.getInstance().props(Props.create(AuthActor.class)), 
            "authRouter"
        );
    }

    public ActorRef getAuthRouter() {
        return authRouter;
    }

    public void terminate() {
        system.terminate();
    }

    public static void main(String[] args) {
        AuthService authService = new AuthService();
        System.out.println("Auth Service started with router: " + authService.getAuthRouter().path());
    }
}
