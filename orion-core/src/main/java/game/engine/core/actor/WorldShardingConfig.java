package game.engine.core.actor;

import org.apache.pekko.cluster.sharding.ShardRegion;

/**
 * World 分片配置
 * 提供 World Actor 的分片策略和消息提取器
 */
public class WorldShardingConfig {

    /**
     * World Actor 类型名称
     */
    public static final String TYPE_NAME = "World";

    /**
     * 分片数量（每个世界一个分片）
     */
    private static final int NUMBER_OF_SHARDS = 100;

    /**
     * World 消息提取器
     * 用于从消息中提取实体ID和分片ID
     */
    public static final ShardRegion.MessageExtractor MESSAGE_EXTRACTOR = new ShardRegion.MessageExtractor() {
        @Override
        public String entityId(Object message) {
            if (message instanceof WorldMessages.WorldMessage) {
                // 使用 worldId 作为实体ID
                return String.valueOf(((WorldMessages.WorldMessage) message).worldId);
            } else if (message instanceof WorldMessages.EnterWorldCommand) {
                return String.valueOf(((WorldMessages.EnterWorldCommand) message).worldId);
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
