package game.engine.world;

import game.engine.core.ProcessType;
import game.engine.world.actor.WorldServiceActor;
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
        // 使用 worldId-1 作为实例ID，这样 worldId=1 对应端口 2560，worldId=2 对应 2561，以此类推
        ActorSystem system = OrionEngine.create()
                .withProcessType(ProcessType.WORLD)
                .withPort(ProcessType.WORLD.getPort(worldId - 1))
                .withDefaultSeedNode() // 连接到 Gateway 实例0
                .start();

        // 创建固定的 World Actor，注册到服务发现
        system.actorOf(WorldServiceActor.props(worldId), "world-" + worldId);
        
        System.out.println("World Server started for World ID: " + worldId + " on port: " + ProcessType.WORLD.getPort(worldId - 1));
    }
}
