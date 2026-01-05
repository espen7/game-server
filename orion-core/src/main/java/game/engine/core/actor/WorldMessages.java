package game.engine.core.actor;

/**
 * World 相关的消息定义
 * 这些消息用于网关和世界服务之间的通信
 */
public class WorldMessages {

    /**
     * World 消息基类
     * 包含玩家ID和世界ID用于路由
     */
    public static class WorldMessage implements java.io.Serializable {
        public final long playerId;
        public final int worldId;
        public final String content;

        public WorldMessage(long playerId, int worldId, String content) {
            this.playerId = playerId;
            this.worldId = worldId;
            this.content = content;
        }
    }

    /**
     * 进入世界命令
     */
    public static class EnterWorldCommand implements java.io.Serializable {
        public final long playerId;
        public final int worldId;

        public EnterWorldCommand(long playerId, int worldId) {
            this.playerId = playerId;
            this.worldId = worldId;
        }
    }
}
