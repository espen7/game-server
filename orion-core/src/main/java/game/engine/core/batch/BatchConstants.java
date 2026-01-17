package game.engine.core.batch;

import java.time.Duration;

/**
 * 批处理模块常量定义
 */
public final class BatchConstants {

    private BatchConstants() {
    }

    // Batch Sizes
    public static final int DB_BATCH_SIZE = 100;
    public static final int SYNC_BATCH_SIZE = 20;

    // Intervals
    public static final long DB_FLUSH_INTERVAL_MS = 5000;
    public static final long SYNC_FLUSH_INTERVAL_MS = 100;

    public static final Duration DB_FLUSH_INTERVAL = Duration.ofMillis(DB_FLUSH_INTERVAL_MS);
    public static final Duration SYNC_FLUSH_INTERVAL = Duration.ofMillis(SYNC_FLUSH_INTERVAL_MS);

    // Retry Config
    public static final int MAX_RETRY = 3;
    public static final long RETRY_DELAY_MS = 1000;
    public static final Duration RETRY_DELAY = Duration.ofMillis(RETRY_DELAY_MS);
}
