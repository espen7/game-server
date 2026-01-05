package game.engine.world.actor;

import game.engine.core.actor.WorldMessages;
import game.engine.core.OrionServices;
import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.Props;
import org.apache.pekko.event.Logging;
import org.apache.pekko.event.LoggingAdapter;

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
        // 注册到服务注册中心，便于 Gateway 查找
        String serviceName = "World-" + worldId;
        OrionServices.registerService(getContext().getSystem(), serviceName, getSelf());
        log.info("WorldServiceActor started for World ID: {}, registered as: {}", worldId, serviceName);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(WorldMessages.WorldMessage.class, this::handleWorldMessage)
                .match(WorldMessages.EnterWorldCommand.class, this::handleEnterWorld)
                .matchAny(msg -> log.info("WorldService received unknown message: {}", msg))
                .build();
    }

    private void handleWorldMessage(WorldMessages.WorldMessage msg) {
        log.info("World {} received message from player {}: {}", worldId, msg.playerId, msg.content);
        // TODO: 处理世界消息逻辑
    }

    private void handleEnterWorld(WorldMessages.EnterWorldCommand cmd) {
        log.info("Player {} entering world {}", cmd.playerId, worldId);
        // TODO: 处理玩家进入世界逻辑
    }
}
