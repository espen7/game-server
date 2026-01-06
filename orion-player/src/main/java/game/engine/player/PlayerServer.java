package game.engine.player;

import game.engine.core.ProcessType;
import game.engine.player.actor.PlayerActor;
import org.apache.pekko.actor.ActorSystem;
import game.engine.core.OrionEngine;

public class PlayerServer {
    public static void main(String[] args) {
        // 解析实例ID（默认为0）
        int instanceId = 0;
        if (args.length > 0) {
            try {
                instanceId = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid instance ID, defaulting to 0");
            }
        }

        // Start as a "player" node
        ActorSystem system = OrionEngine.create()
                .withProcessType(ProcessType.PLAYER)
                .withPort(ProcessType.PLAYER.getPort(instanceId))
                .withDefaultSeedNode() // 连接到 Gateway 实例0
                .start();

        // Initialize Sharding on this node
        PlayerActor.initSharding(system);
        
        System.out.println("Player Server started, instance: " + instanceId + ", port: " + ProcessType.PLAYER.getPort(instanceId));
    }
}
