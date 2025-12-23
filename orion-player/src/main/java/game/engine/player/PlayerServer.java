package game.engine.player;

import org.apache.pekko.actor.ActorSystem;
import game.engine.core.OrionEngine;

public class PlayerServer {
    public static void main(String[] args) {
        // Start as a "player" node
        ActorSystem system = OrionEngine.create()
                .withRole("player")
                .withPort(2554)
                .withSeedNodes("127.0.0.1:2551") // Join the cluster
                .start();

        // Initialize Sharding on this node
        PlayerActor.initSharding(system);
    }
}
