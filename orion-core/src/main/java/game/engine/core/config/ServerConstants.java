package game.engine.core.config;

/**
 * 服务器通用配置常量
 */
public final class ServerConstants {

    private ServerConstants() {
    }

    // Port Ranges
    public static final int GATEWAY_PORT_START = 2551;
    public static final int GATEWAY_PORT_END = 2559;

    public static final int WORLD_PORT_START = 2560;
    public static final int WORLD_PORT_END = 2569;

    public static final int PLAYER_PORT_START = 2570;
    public static final int PLAYER_PORT_END = 2579;

    public static final int PORTAL_PORT_START = 2580;
    public static final int PORTAL_PORT_END = 2589;

    // Database
    public static final int DB_PORT = 3306;
}
