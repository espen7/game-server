package game.engine.core.batch;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * 一个通用的批处理处理器，根据大小或时间间隔累积项目并分批处理�?
 *
 * @param <T> 要批处理的项目类型�?
 */
public class BatchProcessor<T> {
    private final int batchSize;
    private final Duration maxDelay;
    private final Consumer<List<T>> processor;
    private final List<T> buffer;
    private final ReentrantLock lock = new ReentrantLock();
    private final ScheduledExecutorService scheduler;
    private long lastFlushTime;

    public BatchProcessor(int batchSize, Duration maxDelay, Consumer<List<T>> processor) {
        this.batchSize = batchSize;
        this.maxDelay = maxDelay;
        this.processor = processor;
        this.buffer = new ArrayList<>(batchSize);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "BatchProcessor-Scheduler");
            t.setDaemon(true);
            return t;
        });
        this.lastFlushTime = System.currentTimeMillis();

        // 启动定期刷新检�?
        this.scheduler.scheduleAtFixedRate(this::checkFlush, maxDelay.toMillis(), maxDelay.toMillis(), TimeUnit.MILLISECONDS);
    }

    public void add(T item) {
        lock.lock();
        try {
            buffer.add(item);
            if (buffer.size() >= batchSize) {
                flush();
            }
        } finally {
            lock.unlock();
        }
    }

    private void checkFlush() {
        lock.lock();
        try {
            if (!buffer.isEmpty() && (System.currentTimeMillis() - lastFlushTime >= maxDelay.toMillis())) {
                flush();
            }
        } finally {
            lock.unlock();
        }
    }

    public void flush() {
        lock.lock();
        try {
            if (buffer.isEmpty()) {
                return;
            }
            List<T> batch = new ArrayList<>(buffer);
            buffer.clear();
            lastFlushTime = System.currentTimeMillis();
            
            // 异步处理以避免阻塞添�?锁定
            // 或者如果首选同步。我们在调度程序或单独的执行程序中执行此操作吗？
            // 为了安全起见，我们立即运行它，但如果需要，用户应处理线程�?
            try {
                processor.accept(batch);
            } catch (Exception e) {
                // 记录错误但不崩溃
                System.err.println("处理批次时出�? " + e.getMessage());
                e.printStackTrace();
            }
        } finally {
            lock.unlock();
        }
    }

    public void shutdown() {
        flush();
        scheduler.shutdown();
    }
}
