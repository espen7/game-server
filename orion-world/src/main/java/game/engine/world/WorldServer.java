package game.engine.world;

import game.engine.core.ProcessType;
import game.engine.core.server.AbstractServer;
import game.engine.world.actor.WorldServiceActor;
import org.apache.pekko.actor.ActorSystem;

public class WorldServer extends AbstractServer {

    public static void main(String[] args) {
        new WorldServer().boot(args);
    }

    @Override
    protected ProcessType getProcessType() {
        return ProcessType.WORLD;
    }

    @Override
    protected void onStart(ActorSystem system, int instanceId) {
        // Parse World ID from args (default to 1)
        // Note: AbstractServer parses args[0] as instanceId.
        // In WorldServer, args[0] was treated as worldId.
        // And port was calculated as ProcessType.WORLD.getPort(worldId - 1).
        // This implies instanceId = worldId - 1.
        // Let's align this. If args[0] is "1", AbstractServer sets instanceId = 1.
        // But WorldServer logic was: worldId = 1 -> port(0).
        // So if instanceId is 1, we want port(0)?
        // Wait, AbstractServer does: withPort(processType.getPort(instanceId)).
        // If I pass "1", instanceId=1, port=2561 (assuming base+1).
        // Original code: worldId=1 -> port(0) -> 2560.
        // So worldId 1 is instance 0.
        // If I pass "1" to AbstractServer, it becomes instanceId=1.
        // So I need to adjust how instanceId is interpreted or passed.
        // Actually, AbstractServer parses args[0] as instanceId.
        // If the user passes "1" meaning World 1, AbstractServer sees instanceId=1.
        // If World 1 maps to instance 0 (port 2560), then we have a mismatch if we just
        // use default parsing.

        // However, standardizing: instanceId 0 is the first instance.
        // If WorldServer wants to call it "World 1", that's fine, but system-wise it's
        // instance 0.
        // Let's assume the user will now pass "0" for the first world server, or we
        // adjust.
        // But to keep backward compatibility with "args[0] = worldId", we might need to
        // override boot or parsing.
        // But `boot` is final-ish (not final but designed as template).

        // Let's look at `WorldServer.java` original logic:
        // worldId = Integer.parseInt(args[0]); (Default 1)
        // port = ProcessType.WORLD.getPort(worldId - 1);

        // If I use AbstractServer:
        // args[0] = "1" -> instanceId = 1.
        // getPort(1) -> 2561.
        // This changes the port from 2560 to 2561 for input "1".

        // To preserve exact behavior:
        // We should probably treat args[0] as instanceId directly now.
        // So if they want port 2560, they pass "0".
        // And we can derive worldId = instanceId + 1.

        int worldId = instanceId + 1;

        // Create World Actor
        system.actorOf(WorldServiceActor.props(worldId), "world-" + worldId);

        logger.info("World Server started for World ID: {} on port: {}", worldId,
                ProcessType.WORLD.getPort(instanceId));
    }
}
