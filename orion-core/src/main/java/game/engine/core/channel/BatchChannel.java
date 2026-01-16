package game.engine.core.channel;

import game.engine.core.batch.BatchActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 批处理通道抽象基类（基于Actor模型）。
 * 
 * 职责：
 * 1. 封装BatchActor，提供统一的批处理接口
 * 2. 使用Actor消息驱动，无锁设计
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
    private final ActorRef batchActor;
    private final ActorSystem system;
    private final int maxRetry;
    
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
     * @param system Actor系统
     */
    public BatchChannel(String channelName, int batchSize, long flushIntervalMs, ActorSystem system) {
        this(channelName, batchSize, flushIntervalMs, 3, system);
    }
    
    /**
     * 构造函数（带重试次数）
     */
    public BatchChannel(String channelName, int batchSize, long flushIntervalMs, int maxRetry, ActorSystem system) {
        this.channelName = channelName;
        this.maxRetry = maxRetry;
        this.system = system;
        
        // 创建BatchActor，复用现有批处理逻辑
        this.batchActor = system.actorOf(
            BatchActor.props(
                batchSize,
                Duration.ofMillis(flushIntervalMs),
                this::processBatchWithRetry  // 包装重试逻辑
            ),
            "batch-channel-" + channelName
        );
        
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
     * 提交数据到批处理Actor
     * 
     * @param item 数据项
     */
    public void submit(T item) {
        batchActor.tell(new BatchActor.Add<>(item), ActorRef.noSender());
    }
    

    
    /**
     * 带重试的批处理（由BatchActor调用）
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
            0  // Actor内部队列大小，暂不暴露
        );
    }
    
    /**
     * 优雅关闭通道
     */
    public void shutdown() {
        logger.info("[{}] Shutting down channel...", channelName);
        
        // 发送Flush消息确保处理剩余数据，然后停止Actor
        batchActor.tell(new BatchActor.Flush(), ActorRef.noSender());
        system.stop(batchActor);
        
        ChannelMetrics metrics = getMetrics();
        logger.info("[{}] Channel shut down. Final metrics: {}", channelName, metrics);
    }
    
    /**
     * 获取Actor引用（用于高级操作）
     */
    protected ActorRef getBatchActor() {
        return batchActor;
    }
}
