package game.engine.core.batch;

import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.Cancellable;
import org.apache.pekko.actor.Props;
import org.apache.pekko.japi.pf.ReceiveBuilder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * 基于 Actor 的异步批处理处理器。
 * 
 * 改进：
 * 1. 使用 Function<List<T>, CompletionStage<Void>> 替代 Consumer
 * 2. 移除阻塞操作，使用状态切换处理异步结果
 * 3. 实现非阻塞的重试机制
 *
 * @param <T> 批处理项目的类型
 */
public class BatchActor<T> extends AbstractActor {

    public record Add<T>(T item) {
    }

    public static class Flush {
    }

    private static class Retry {
    }

    private record ProcessResult(boolean success, Throwable error) {
    }

    private final int batchSize;
    private final Duration maxDelay;
    private final Function<List<T>, CompletionStage<Void>> processor;
    private final List<T> buffer;
    private Cancellable flushTask;

    // 重试配置
    private final int maxRetry;
    private int currentRetry = 0;
    private List<T> pendingBatch; // 正在处理或等待重试的批次

    public static <T> Props props(int batchSize, Duration maxDelay, int maxRetry,
            Function<List<T>, CompletionStage<Void>> processor) {
        return Props.create(BatchActor.class, () -> new BatchActor<>(batchSize, maxDelay, maxRetry, processor));
    }

    public BatchActor(int batchSize, Duration maxDelay, int maxRetry,
            Function<List<T>, CompletionStage<Void>> processor) {
        this.batchSize = batchSize;
        this.maxDelay = maxDelay;
        this.maxRetry = maxRetry;
        this.processor = processor;
        this.buffer = new ArrayList<>(batchSize);
    }

    @Override
    public void preStart() {
        scheduleFlush();
    }

    @Override
    public void postStop() {
        if (flushTask != null) {
            flushTask.cancel();
        }
    }

    @Override
    public Receive createReceive() {
        return active();
    }

    /**
     * 活跃状态：接收新数据，处理刷新
     */
    private Receive active() {
        return ReceiveBuilder.create()
                .match(Add.class, msg -> {
                    @SuppressWarnings("unchecked")
                    T item = (T) msg.item;
                    buffer.add(item);
                    if (buffer.size() >= batchSize) {
                        doFlush();
                    }
                })
                .match(Flush.class, msg -> doFlush())
                .build();
    }

    /**
     * 等待结果状态：缓冲新数据，但不触发新处理，直到当前处理完成
     */
    private Receive waitingForResult() {
        return ReceiveBuilder.create()
                .match(Add.class, msg -> {
                    @SuppressWarnings("unchecked")
                    T item = (T) msg.item;
                    buffer.add(item);
                })
                .match(Flush.class, msg -> {
                    // 忽略 Flush，因为正在处理中
                })
                .match(ProcessResult.class, res -> {
                    if (res.success) {
                        onProcessSuccess();
                    } else {
                        onProcessFailure(res.error);
                    }
                })
                .build();
    }

    /**
     * 等待重试状态：缓冲新数据
     */
    private Receive waitingForRetry() {
        return ReceiveBuilder.create()
                .match(Add.class, msg -> {
                    @SuppressWarnings("unchecked")
                    T item = (T) msg.item;
                    buffer.add(item);
                })
                .match(Flush.class, msg -> {
                    // 忽略 Flush
                })
                .match(Retry.class, msg -> {
                    retryProcess();
                })
                .build();
    }

    private void doFlush() {
        if (buffer.isEmpty()) {
            return;
        }

        // 准备批次
        pendingBatch = new ArrayList<>(buffer);
        buffer.clear();
        currentRetry = 0;

        // 切换状态并开始处理
        getContext().become(waitingForResult());
        executeProcess();

        // 重置定时器
        rescheduleFlush();
    }

    private void executeProcess() {
        processor.apply(pendingBatch).handle((v, ex) -> {
            // 将结果发送回 Actor (确保线程安全)
            getSelf().tell(new ProcessResult(ex == null, ex), getSelf());
            return null;
        });
    }

    private void onProcessSuccess() {
        pendingBatch = null;
        currentRetry = 0;
        // 恢复活跃状态
        getContext().become(active());

        // 如果缓冲区已满，立即再次刷新
        if (buffer.size() >= batchSize) {
            getSelf().tell(new Flush(), getSelf());
        }
    }

    private void onProcessFailure(Throwable error) {
        if (currentRetry < maxRetry) {
            currentRetry++;
            long delay = BatchConstants.RETRY_DELAY_MS * currentRetry; // 简单线性退避

            getContext().getSystem().log().warning(
                    "Batch processing failed (attempt {}/{}): {}. Retrying in {}ms",
                    currentRetry, maxRetry, error.getMessage(), delay);

            getContext().become(waitingForRetry());
            getContext().getSystem().scheduler().scheduleOnce(
                    Duration.ofMillis(delay),
                    getSelf(),
                    new Retry(),
                    getContext().getDispatcher(),
                    getSelf());
        } else {
            getContext().getSystem().log().error(
                    "Batch processing failed after {} retries. Dropping batch of {} items. Error: {}",
                    maxRetry, pendingBatch.size(), error.getMessage());
            // 放弃，恢复活跃状态
            pendingBatch = null;
            currentRetry = 0;
            getContext().become(active());
        }
    }

    private void retryProcess() {
        getContext().become(waitingForResult());
        executeProcess();
    }

    private void scheduleFlush() {
        flushTask = getContext().getSystem().scheduler().scheduleWithFixedDelay(
                maxDelay,
                maxDelay,
                getSelf(),
                new Flush(),
                getContext().getDispatcher(),
                getSelf());
    }

    private void rescheduleFlush() {
        if (flushTask != null) {
            flushTask.cancel();
        }
        scheduleFlush();
    }
}
