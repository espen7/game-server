package game.engine.player;

import game.engine.core.ProcessType;
import game.engine.core.server.AbstractServer;
import game.engine.player.actor.PlayerActor;
import org.apache.pekko.actor.ActorSystem;

public class PlayerServer extends AbstractServer {

    public static void main(String[] args) {
        new PlayerServer().boot(args);
    }

    @Override
    protected ProcessType getProcessType() {
        return ProcessType.PLAYER;
    }

    @Override
    protected void onStart(ActorSystem system, int instanceId) {
        // Initialize Sharding on this node
        PlayerActor.initSharding(system);
    }
}
