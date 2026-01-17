package game.engine.core.channel;

import game.engine.core.batch.BatchActor;
import game.engine.core.batch.BatchConstants;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
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
 * - processBatchAsync(List<T>): 异步批量处理逻辑
 * 
 * @param <T> 批处理项目的类型
 */
public abstract class BatchChannel<T> {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private final String channelName;
    private final ActorRef batchActor;
    private final ActorSystem system;

    // 监控指标
    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);

    /**
     * 构造函数
     * 
     * @param channelName     通道名称
     * @param batchSize       批次大小
     * @param flushIntervalMs 刷新间隔（毫秒）
     * @param system          Actor系统
     */
    public BatchChannel(String channelName, int batchSize, long flushIntervalMs, ActorSystem system) {
        this(channelName, batchSize, flushIntervalMs, BatchConstants.MAX_RETRY, system);
    }

    /**
     * 构造函数（带重试次数）
     */
    public BatchChannel(String channelName, int batchSize, long flushIntervalMs, int maxRetry, ActorSystem system) {
        this.channelName = channelName;
        this.system = system;

        // 创建BatchActor，复用现有批处理逻辑
        this.batchActor = system.actorOf(
                BatchActor.props(
                        batchSize,
                        Duration.ofMillis(flushIntervalMs),
                        maxRetry,
                        this::processBatchInternal // 内部处理包装
                ),
                "batch-channel-" + channelName);

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
     * 异步批量处理逻辑（由子类实现）
     * 
     * @param batch 批次数据
     * @return CompletionStage 表示处理完成
     */
    protected abstract CompletionStage<Void> processBatchAsync(List<T> batch);

    /**
     * 提交数据到批处理Actor
     * 
     * @param item 数据项
     */
    public void submit(T item) {
        batchActor.tell(new BatchActor.Add<>(item), ActorRef.noSender());
    }

    /**
     * 内部处理逻辑，负责更新指标
     */
    private CompletionStage<Void> processBatchInternal(List<T> batch) {
        return processBatchAsync(batch)
                .whenComplete((v, ex) -> {
                    if (ex == null) {
                        processedCount.addAndGet(batch.size());
                    } else {
                        // 只有最终失败才会在这里记录（BatchActor重试失败后）
                        // 注意：BatchActor的重试是在Actor内部处理的，这里看到的是最终结果
                        // 如果BatchActor重试成功，这里看到的是成功
                        // 如果BatchActor重试多次后放弃，这里看到的是失败
                        failedCount.addAndGet(batch.size());
                        logger.error("[{}] Batch processing failed: {}", channelName, ex.getMessage());
                    }
                });
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
                0, // 重试次数现在由BatchActor管理，外部较难获取准确值，暂置0
                0);
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
