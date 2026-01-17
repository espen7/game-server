package game.engine.core.rpc;

import java.time.Duration;

/**
 * RPC 模块常量定义
 */
public final class RpcConstants {

    private RpcConstants() {
    }

    // Timeouts
    public static final long DEFAULT_TIMEOUT_MS = 5000;
    public static final long RESOLVE_TIMEOUT_MS = 3000;
    public static final long LONG_RUNNING_TIMEOUT_MS = 10000;

    public static final Duration DEFAULT_TIMEOUT = Duration.ofMillis(DEFAULT_TIMEOUT_MS);
    public static final Duration RESOLVE_TIMEOUT = Duration.ofMillis(RESOLVE_TIMEOUT_MS);

    // Retries
    public static final int DEFAULT_RETRIES = 3;
    public static final int AGGRESSIVE_RETRIES = 2;

    // Limits
    public static final int MAX_CALLS_PER_MINUTE = 100;
}
