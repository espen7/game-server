package game.engine.core;

import org.apache.pekko.actor.ActorSystem;

public class OrionContext {
    private static volatile ActorSystem system;

    public static void setSystem(ActorSystem system) {
        OrionContext.system = system;
    }

    public static ActorSystem getSystem() {
        if (system == null) {
            throw new IllegalStateException("OrionContext has not been initialized. Call OrionEngine.start() first.");
        }
        return system;
    }
}
