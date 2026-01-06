package game.engine.core.actor;

/**
 * Portal 相关的消息定义
 * 用于对外服务（认证、HTTP API、SDK 等）的请求和响应
 */
public class PortalMessages {

    /**
     * 认证请求
     */
    public static class AuthRequest implements java.io.Serializable {
        public final String username;
        public final String password;
        public final String token;

        public AuthRequest(String username, String password, String token) {
            this.username = username;
            this.password = password;
            this.token = token;
        }
    }

    /**
     * 认证响应
     */
    public static class AuthResponse implements java.io.Serializable {
        public final boolean success;
        public final long playerId;
        public final long accountId;
        public final String message;

        public AuthResponse(boolean success, long playerId, long accountId, String message) {
            this.success = success;
            this.playerId = playerId;
            this.accountId = accountId;
            this.message = message;
        }

        public static AuthResponse success(long playerId, long accountId) {
            return new AuthResponse(true, playerId, accountId, "Authentication successful");
        }

        public static AuthResponse failure(String message) {
            return new AuthResponse(false, 0, 0, message);
        }
    }
}
