package game.engine.player.actor;

import game.engine.core.actor.PlayerMessages;
import game.engine.core.actor.PlayerShardingConfig;
import game.engine.core.channel.DeltaPublisher;
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
    private final DeltaPublisher publisher = DeltaPublisher.getInstance();

    private static final int PASSIVATION_TIMEOUT_MINUTES = 30;

    public PlayerActor() {
        this.playerId = Long.parseLong(getSelf().path().name());
    }

    public static Props props() {
        return Props.create(PlayerActor.class);
    }

    public static ActorRef initSharding(org.apache.pekko.actor.ActorSystem system) {
        return ClusterSharding.get(system).start(
                PlayerShardingConfig.TYPE_NAME,
                Props.create(PlayerActor.class),
                ClusterShardingSettings.create(system),
                PlayerShardingConfig.MESSAGE_EXTRACTOR);
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
                .match(PlayerMessages.PlayerLoginCommand.class, this::handleLogin)
                .matchAny(msg -> {
                    log.info("Stashing message in uninitialized state: {}", msg);
                    stash();
                })
                .build();
    }

    private Receive running() {
        return receiveBuilder()
                .match(PlayerMessages.PlayerMessage.class, msg -> {
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

    private void handleLogin(PlayerMessages.PlayerLoginCommand cmd) {
        log.info("Processing login for player: {}", playerId);
        try (SqlSession session = MyBatisUtil.getSqlSessionFactory().openSession()) {
            PlayerMapper mapper = session.getMapper(PlayerMapper.class);
            this.player = mapper.selectById(playerId);

            if (this.player == null) {
                log.info("Player not found, creating new player: {}", playerId);
                this.player = new Player(playerId, cmd.accountId);
                this.player.setNickname("Player" + playerId); // Default name
                // 新玩家，状态为 TRANSIENT
                mapper.insert(this.player);
                session.commit();
                // 插入成功后，标记为 MANAGED
                this.player.onPersisted();
            } else {
                log.info("Player loaded: {}", player.getNickname());
                // 从数据库载入后，标记为 MANAGED 并清除脏标记
                this.player.onLoaded();
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
            // 使用新的发布系统
            publisher.publish(player);
            log.info("Player changes published: {}", playerId);
        }
    }

    // RPC相关消息定义
    
    /**
     * 获取玩家信息请求
     */
    public static class GetPlayerInfo implements java.io.Serializable {}
    
    /**
     * 玩家信息响应
     */
    public static class PlayerInfoResponse implements java.io.Serializable {
        private final Player player;
        private final String error;
        private final boolean success;
        
        public PlayerInfoResponse(Player player) {
            this.player = player;
            this.error = null;
            this.success = true;
        }
        
        public PlayerInfoResponse(String error) {
            this.player = null;
            this.error = error;
            this.success = false;
        }
        
        public Player getPlayer() { return player; }
        public String getError() { return error; }
        public boolean isSuccess() { return success; }
    }
    
    /**
     * 更新等级命令
     */
    public static class UpdateLevelCommand implements java.io.Serializable {
        private final int newLevel;
        
        public UpdateLevelCommand(int newLevel) {
            this.newLevel = newLevel;
        }
        
        public int getNewLevel() { return newLevel; }
    }
    
    /**
     * 检查在线状态请求
     */
    public static class CheckOnlineStatus implements java.io.Serializable {}
    
    /**
     * 在线状态响应
     */
    public static class OnlineStatusResponse implements java.io.Serializable {
        private final boolean online;
        
        public OnlineStatusResponse(boolean online) {
            this.online = online;
        }
        
        public boolean isOnline() { return online; }
    }
    
    /**
     * 发送系统消息
     */
    public static class SendSystemMessage implements java.io.Serializable {
        private final String message;
        
        public SendSystemMessage(String message) {
            this.message = message;
        }
        
        public String getMessage() { return message; }
    }
    
    /**
     * 命令执行结果
     */
    public static class CommandResult implements java.io.Serializable {
        private final boolean success;
        private final String error;
        
        public CommandResult(boolean success) {
            this(success, null);
        }
        
        public CommandResult(boolean success, String error) {
            this.success = success;
            this.error = error;
        }
        
        public boolean isSuccess() { return success; }
        public String getError() { return error; }
    }
    
    private static class StopMessage implements java.io.Serializable {
    }
}
