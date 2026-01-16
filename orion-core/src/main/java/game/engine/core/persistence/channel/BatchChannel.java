package game.engine.core.persistence.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 批处理通道抽象基类。
 * 
 * 职责：
 * 1. 提供通用的批处理逻辑（队列、批次大小、刷新间隔）
 * 2. 使用虚拟线程进行异步处理
 * 3. 支持错误重试和失败回调
 * 4. 提供监控指标
 * 
 * 子类只需实现：
 * - accepts(Class<?>): 判断是否处理该类型实体
 * - processBatch(List<T>): 批量处理逻辑
 * - onProcessFailed(List<T>, Exception): 失败处理逻辑（可选）
 * 
 * @param <T> 批处理项目的类型
 */
public abstract class BatchChannel<T> {
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    
    private final String channelName;
    private final BlockingQueue<T> queue;
    private final ExecutorService executor;
    private final int batchSize;
    private final long flushIntervalMs;
    private final int maxRetry;
    private volatile boolean running = true;
    
    // 监控指标
    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);
    private final AtomicLong retryCount = new AtomicLong(0);
    
    /**
     * 构造函数
     * 
     * @param channelName 通道名称
     * @param batchSize 批次大小
     * @param flushIntervalMs 刷新间隔（毫秒）
     */
    public BatchChannel(String channelName, int batchSize, long flushIntervalMs) {
        this(channelName, batchSize, flushIntervalMs, 3);
    }
    
    /**
     * 构造函数（带重试次数）
     */
    public BatchChannel(String channelName, int batchSize, long flushIntervalMs, int maxRetry) {
        this.channelName = channelName;
        this.batchSize = batchSize;
        this.flushIntervalMs = flushIntervalMs;
        this.maxRetry = maxRetry;
        this.queue = new LinkedBlockingQueue<>();
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        
        // 启动批处理循环
        executor.submit(this::processLoop);
        logger.info("Channel [{}] started: batchSize={}, flushInterval={}ms, maxRetry={}", 
            channelName, batchSize, flushIntervalMs, maxRetry);
    }
    
    /**
     * 判断是否处理该类型实体（由子类实现）
     * 
     * @param entityClass 实体类型
     * @return true表示接受处理
     */
    public abstract boolean accepts(Class<?> entityClass);
    
    /**
     * 批量处理逻辑（由子类实现）
     * 
     * @param batch 批次数据
     * @throws Exception 处理异常
     */
    protected abstract void processBatch(List<T> batch) throws Exception;
    
    /**
     * 处理失败回调（子类可选实现）
     * 
     * @param batch 失败的批次
     * @param e 异常信息
     */
    protected void onProcessFailed(List<T> batch, Exception e) {
        logger.error("[{}] Failed to process batch of {} items after {} retries", 
            channelName, batch.size(), maxRetry, e);
    }
    
    /**
     * 提交数据到批处理队列
     * 
     * @param item 数据项
     */
    public void submit(T item) {
        if (!running) {
            logger.warn("[{}] Channel is shutting down, rejecting item", channelName);
            return;
        }
        
        boolean success = queue.offer(item);
        if (!success) {
            logger.error("[{}] Queue is full, item rejected", channelName);
        }
    }
    
    /**
     * 批处理主循环
     */
    private void processLoop() {
        List<T> batch = new ArrayList<>(batchSize);
        long lastFlushTime = System.currentTimeMillis();
        
        logger.info("[{}] Process loop started", channelName);
        
        while (running) {
            try {
                // 非阻塞poll，超时100ms
                T item = queue.poll(100, TimeUnit.MILLISECONDS);
                if (item != null) {
                    batch.add(item);
                }
                
                long now = System.currentTimeMillis();
                boolean shouldFlush = !batch.isEmpty() && 
                    (batch.size() >= batchSize || now - lastFlushTime >= flushIntervalMs);
                
                if (shouldFlush) {
                    processBatchWithRetry(batch);
                    batch.clear();
                    lastFlushTime = now;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("[{}] Process loop interrupted", channelName);
                break;
            } catch (Exception e) {
                logger.error("[{}] Error in process loop", channelName, e);
            }
        }
        
        // 关闭前处理剩余数据
        if (!batch.isEmpty()) {
            logger.info("[{}] Flushing remaining {} items before shutdown", channelName, batch.size());
            processBatchWithRetry(batch);
        }
        
        logger.info("[{}] Process loop stopped", channelName);
    }
    
    /**
     * 带重试的批处理
     */
    private void processBatchWithRetry(List<T> batch) {
        int attempt = 0;
        long retryDelay = 1000; // 初始重试延迟1秒
        
        while (attempt < maxRetry) {
            try {
                processBatch(batch);
                processedCount.addAndGet(batch.size());
                
                if (attempt > 0) {
                    logger.info("[{}] Batch processed successfully on retry {}", channelName, attempt);
                }
                return; // 成功
            } catch (Exception e) {
                attempt++;
                retryCount.incrementAndGet();
                
                if (attempt >= maxRetry) {
                    // 最后一次重试失败
                    failedCount.addAndGet(batch.size());
                    onProcessFailed(batch, e);
                    break;
                }
                
                logger.warn("[{}] Batch processing failed (attempt {}/{}): {}", 
                    channelName, attempt, maxRetry, e.getMessage());
                
                // 指数退避重试
                try {
                    Thread.sleep(retryDelay * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
    
    /**
     * 获取通道名称
     */
    public String getChannelName() {
        return channelName;
    }
    
    /**
     * 获取监控指标
     */
    public ChannelMetrics getMetrics() {
        return new ChannelMetrics(
            channelName,
            processedCount.get(),
            failedCount.get(),
            retryCount.get(),
            queue.size()
        );
    }
    
    /**
     * 优雅关闭通道
     */
    public void shutdown() {
        logger.info("[{}] Shutting down channel...", channelName);
        running = false;
        
        try {
            executor.shutdown();
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warn("[{}] Executor did not terminate in time, forcing shutdown", channelName);
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
        
        ChannelMetrics metrics = getMetrics();
        logger.info("[{}] Channel shut down. Final metrics: {}", channelName, metrics);
    }
}
