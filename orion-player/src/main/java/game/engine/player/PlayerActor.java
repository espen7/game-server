package game.engine.player;

import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import org.apache.pekko.cluster.sharding.ClusterSharding;
import org.apache.pekko.cluster.sharding.ClusterShardingSettings;
import org.apache.pekko.cluster.sharding.ShardRegion;
import org.apache.pekko.event.Logging;
import org.apache.pekko.event.LoggingAdapter;

public class PlayerActor extends AbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    private final String playerId;

    public PlayerActor(String playerId) {
        this.playerId = playerId;
    }

    public static Props props(String playerId) {
        return Props.create(PlayerActor.class, () -> new PlayerActor(playerId));
    }

    // Sharding Configuration
    public static final String TYPE_NAME = "Player";

    public static final ShardRegion.MessageExtractor messageExtractor = new ShardRegion.MessageExtractor() {
        @Override
        public String entityId(Object message) {
            if (message instanceof PlayerMessage) {
                return ((PlayerMessage) message).playerId;
            }
            return null;
        }

        @Override
        public Object entityMessage(Object message) {
            return message;
        }

        @Override
        public String shardId(Object message) {
            int numberOfShards = 100;
            if (message instanceof PlayerMessage) {
                String id = ((PlayerMessage) message).playerId;
                return String.valueOf(Math.abs(id.hashCode()) % numberOfShards);
            }
            return null;
        }
    };

    public static ActorRef initSharding(org.apache.pekko.actor.ActorSystem system) {
        return ClusterSharding.get(system).start(
                TYPE_NAME,
                Props.create(PlayerActor.class, () -> new PlayerActor("unknown")), // Note: Constructor arg is tricky with Sharding, usually passed via message or EntityId
                ClusterShardingSettings.create(system),
                messageExtractor
        );
    }
    
    // Fix for constructor injection in Sharding: 
    // Usually we don't pass ID in constructor for Sharding, we get it from self().path().name() or the first message.
    // Let's adjust to get ID from self path which is the EntityID.
    public PlayerActor() {
        this.playerId = getSelf().path().name();
    }

    @Override
    public void preStart() {
        log.info("PlayerActor started: {}", playerId);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(PlayerMessage.class, msg -> {
                    log.info("Player {} received message: {}", playerId, msg.content);
                })
                .build();
    }

    // Simple wrapper for messages
    public static class PlayerMessage implements java.io.Serializable {
        public final String playerId;
        public final String content;

        public PlayerMessage(String playerId, String content) {
            this.playerId = playerId;
            this.content = content;
        }
    }
}
