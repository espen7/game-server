package game.engine.world;

import org.apache.pekko.actor.ActorSystem;
import game.engine.core.OrionEngine;

public class WorldServer {
    public static void main(String[] args) {
        // Parse World ID from args (default to 1)
        int worldId = 1;
        if (args.length > 0) {
            try {
                worldId = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid World ID, defaulting to 1");
            }
        }
        
        // Start as a "world" node
        ActorSystem system = OrionEngine.create()
                .withRole("world")
                .withPort(2553 + worldId - 1) // Avoid port conflict if running multiple locally
                .withSeedNodes("127.0.0.1:2551") // Join the cluster
                .start();

        system.actorOf(WorldServiceActor.props(worldId), "worldService-" + worldId);
    }
}
