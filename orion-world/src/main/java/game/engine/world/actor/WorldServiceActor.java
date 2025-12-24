package game.engine.world.actor;

import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.Props;
import org.apache.pekko.event.Logging;
import org.apache.pekko.event.LoggingAdapter;
import game.engine.core.OrionServices;

public class WorldServiceActor extends AbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    private final int worldId;

    public WorldServiceActor(int worldId) {
        this.worldId = worldId;
    }

    public static Props props(int worldId) {
        return Props.create(WorldServiceActor.class, () -> new WorldServiceActor(worldId));
    }

    @Override
    public void preStart() {
        // Register as "WorldService-{worldId}"
        OrionServices.registerService(getContext().getSystem(), "WorldService-" + worldId, getSelf());
        log.info("WorldService started on World Node for World ID: {}", worldId);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .matchAny(msg -> log.info("WorldService received: {}", msg))
                .build();
    }
}
