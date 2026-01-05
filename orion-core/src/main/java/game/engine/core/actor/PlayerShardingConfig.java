package game.engine.core.actor;

import org.apache.pekko.cluster.sharding.ShardRegion;

/**
 * 玩家分片配置
 * 提供玩家Actor的分片策略和消息提取器
 */
public class PlayerShardingConfig {

    /**
     * 玩家Actor类型名称
     */
    public static final String TYPE_NAME = "Player";

    /**
     * 分片数量
     */
    private static final int NUMBER_OF_SHARDS = 100;

    /**
     * 玩家消息提取器
     * 用于从消息中提取实体ID和分片ID
     */
    public static final ShardRegion.MessageExtractor MESSAGE_EXTRACTOR = new ShardRegion.MessageExtractor() {
        @Override
        public String entityId(Object message) {
            if (message instanceof PlayerMessages.PlayerMessage) {
                return String.valueOf(((PlayerMessages.PlayerMessage) message).playerId);
            } else if (message instanceof PlayerMessages.PlayerLoginCommand) {
                return String.valueOf(((PlayerMessages.PlayerLoginCommand) message).playerId);
            }
            return null;
        }

        @Override
        public Object entityMessage(Object message) {
            return message;
        }

        @Override
        public String shardId(Object message) {
            String id = entityId(message);
            if (id != null) {
                return String.valueOf(Math.abs(id.hashCode()) % NUMBER_OF_SHARDS);
            }
            return null;
        }
    };
}
