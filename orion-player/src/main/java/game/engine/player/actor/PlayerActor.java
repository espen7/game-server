package game.engine.player.actor;

import game.engine.gateway.proto.GatewayProto;
import game.engine.player.entity.Player;
import game.engine.player.persistence.MyBatisUtil;
import game.engine.player.persistence.mapper.PlayerMapper;
import org.apache.ibatis.session.SqlSession;
import org.apache.pekko.actor.AbstractActorWithStash;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import org.apache.pekko.actor.ReceiveTimeout;
import org.apache.pekko.cluster.sharding.ClusterSharding;
import org.apache.pekko.cluster.sharding.ClusterShardingSettings;
import org.apache.pekko.cluster.sharding.ShardRegion;
import org.apache.pekko.event.Logging;
import org.apache.pekko.event.LoggingAdapter;
import scala.concurrent.duration.Duration;

import java.util.concurrent.TimeUnit;

public class PlayerActor extends AbstractActorWithStash {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    private final long playerId;
    private Player player;

    public static final String TYPE_NAME = "Player";
    private static final int PASSIVATION_TIMEOUT_MINUTES = 30;

    public PlayerActor() {
        this.playerId = Long.parseLong(getSelf().path().name());
    }

    public static Props props() {
        return Props.create(PlayerActor.class);
    }

    // Sharding Configuration
    public static final ShardRegion.MessageExtractor messageExtractor = new ShardRegion.MessageExtractor() {
        @Override
        public String entityId(Object message) {
            if (message instanceof PlayerMessage) {
                return String.valueOf(((PlayerMessage) message).playerId);
            } else if (message instanceof PlayerLoginCommand) {
                return String.valueOf(((PlayerLoginCommand) message).playerId);
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
            String id = entityId(message);
            if (id != null) {
                return String.valueOf(Math.abs(id.hashCode()) % numberOfShards);
            }
            return null;
        }
    };

    public static ActorRef initSharding(org.apache.pekko.actor.ActorSystem system) {
        return ClusterSharding.get(system).start(
                TYPE_NAME,
                Props.create(PlayerActor.class),
                ClusterShardingSettings.create(system),
                messageExtractor);
    }

    @Override
    public void preStart() {
        log.info("PlayerActor started: {}", playerId);
        getContext().setReceiveTimeout(Duration.create(PASSIVATION_TIMEOUT_MINUTES, TimeUnit.MINUTES));
    }

    @Override
    public Receive createReceive() {
        return uninitialized();
    }

    private Receive uninitialized() {
        return receiveBuilder()
                .match(PlayerLoginCommand.class, this::handleLogin)
                .matchAny(msg -> {
                    log.info("Stashing message in uninitialized state: {}", msg);
                    stash();
                })
                .build();
    }

    private Receive running() {
        return receiveBuilder()
                .match(PlayerMessage.class, msg -> {
                    log.info("Player {} received message: {}", playerId, msg.content);
                    // Process message
                })
                .match(ReceiveTimeout.class, msg -> {
                    log.info("Player {} receive timeout, passivating...", playerId);
                    getContext().getParent().tell(new ShardRegion.Passivate(new StopMessage()), getSelf());
                })
                .match(StopMessage.class, msg -> {
                    log.info("Player {} stopping...", playerId);
                    savePlayer(); // Save before stop
                    getContext().stop(getSelf());
                })
                .matchAny(msg -> log.warning("Unknown message in running state: {}", msg))
                .build();
    }

    private void handleLogin(PlayerLoginCommand cmd) {
        log.info("Processing login for player: {}", playerId);
        try (SqlSession session = MyBatisUtil.getSqlSessionFactory().openSession()) {
            PlayerMapper mapper = session.getMapper(PlayerMapper.class);
            this.player = mapper.selectById(playerId);

            if (this.player == null) {
                log.info("Player not found, creating new player: {}", playerId);
                this.player = new Player(playerId, cmd.accountId);
                this.player.setNickname("Player" + playerId); // Default name
                mapper.insert(this.player);
                session.commit();
            } else {
                log.info("Player loaded: {}", player.getNickname());
            }

            // Reply with EnterGameResp
            GatewayProto.EnterGameResp resp = GatewayProto.EnterGameResp.newBuilder()
                    .setUid(playerId)
                    .setCode(0)
                    .setMsg("Success")
                    .setNickname(player.getNickname())
                    .setLevel(player.getLevel())
                    .build();

            getSender().tell(resp, getSelf());

            // Switch state
            getContext().become(running());
            unstashAll();

        } catch (Exception e) {
            log.error(e, "Error during login/loading player: {}", playerId);
            // Reply error
            GatewayProto.EnterGameResp resp = GatewayProto.EnterGameResp.newBuilder()
                    .setUid(playerId)
                    .setCode(1)
                    .setMsg("Internal Error")
                    .build();
            getSender().tell(resp, getSelf());
            // Stop self?
            getContext().stop(getSelf());
        }
    }

    private void savePlayer() {
        if (player != null && player.isDirty()) {
            try (SqlSession session = MyBatisUtil.getSqlSessionFactory().openSession()) {
                PlayerMapper mapper = session.getMapper(PlayerMapper.class);
                mapper.update(player);
                session.commit();
                player.clearDirty();
                log.info("Player saved: {}", playerId);
            } catch (Exception e) {
                log.error(e, "Error saving player: {}", playerId);
            }
        }
    }

    // Messages
    public static class PlayerMessage implements java.io.Serializable {
        public final long playerId;
        public final String content;

        public PlayerMessage(long playerId, String content) {
            this.playerId = playerId;
            this.content = content;
        }
    }

    public static class PlayerLoginCommand implements java.io.Serializable {
        public final long playerId;
        public final long accountId;

        public PlayerLoginCommand(long playerId, long accountId) {
            this.playerId = playerId;
            this.accountId = accountId;
        }
    }

    private static class StopMessage implements java.io.Serializable {
    }
}
