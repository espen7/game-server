package game.engine.core.actor;

/**
 * 玩家相关的消息定义
 * 这些消息用于网关和玩家服务之间的通信
 */
public class PlayerMessages {

    /**
     * 玩家消息基类
     */
    public static class PlayerMessage implements java.io.Serializable {
        public final long playerId;
        public final String content;

        public PlayerMessage(long playerId, String content) {
            this.playerId = playerId;
            this.content = content;
        }
    }

    /**
     * 玩家登录命令
     */
    public static class PlayerLoginCommand implements java.io.Serializable {
        public final long playerId;
        public final long accountId;

        public PlayerLoginCommand(long playerId, long accountId) {
            this.playerId = playerId;
            this.accountId = accountId;
        }
    }
}
