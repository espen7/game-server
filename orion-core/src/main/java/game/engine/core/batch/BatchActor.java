package game.engine.core.batch;

import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.Cancellable;
import org.apache.pekko.actor.Props;
import org.apache.pekko.japi.pf.ReceiveBuilder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 基于 Actor 的批处理处理器。
 * 利用 Actor 模型避免显式锁，适合集成到 Akka 系统中。
 *
 * @param <T> 批处理项目的类型
 */
public class BatchActor<T> extends AbstractActor {

    /**
     * 添加单个项目到批处理缓冲区的消息。
     */
    public record Add<T>(T item) {
    }

    /**
     * 触发立即刷新缓冲区的消息。
     */
    public static class Flush {
    }

    /** 批处理的最大大小，达到此大小将立即触发处理。 */
    private final int batchSize;
    /** 两次刷新之间的最大时间间隔。 */
    private final Duration maxDelay;
    /** 实际处理批次数据的消费者逻辑。 */
    private final Consumer<List<T>> processor;
    /** 内部缓冲区，用于累积项目。 */
    private final List<T> buffer;
    /** 定时刷新任务的句柄，用于取消或重新调度。 */
    private Cancellable flushTask;

    /**
     * 创建 BatchActor 的 Props。
     *
     * @param batchSize 批次大小
     * @param maxDelay 最大延迟时间
     * @param processor 批处理逻辑
     * @param <T> 项目类型
     * @return Actor Props
     */
    public static <T> Props props(int batchSize, Duration maxDelay, Consumer<List<T>> processor) {
        return Props.create(BatchActor.class, () -> new BatchActor<>(batchSize, maxDelay, processor));
    }

    public BatchActor(int batchSize, Duration maxDelay, Consumer<List<T>> processor) {
        this.batchSize = batchSize;
        this.maxDelay = maxDelay;
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
        // 停止时尝试处理剩余数据
        flush();
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(Add.class, msg -> {
                    @SuppressWarnings("unchecked")
                    T item = (T) msg.item;
                    buffer.add(item);
                    if (buffer.size() >= batchSize) {
                        flush();
                        // 重置定时器以避免空闲时刷新
                        rescheduleFlush();
                    }
                })
                .match(Flush.class, msg -> flush())
                .build();
    }

    /**
     * 刷新缓冲区，将当前累积的所有项目发送给处理器。
     */
    private void flush() {
        if (buffer.isEmpty()) {
            return;
        }
        List<T> batch = new ArrayList<>(buffer);
        buffer.clear();

        try {
            // 注意：processor 在 Actor 线程中运行。
            // 如果处理逻辑耗时，应将其放入 Future 或发送给另一个 Actor。
            processor.accept(batch);
        } catch (Exception e) {
            getContext().getSystem().log().error("Batch processing failed: {}", e.getMessage());
        }
    }

    /**
     * 调度定期的刷新任务。
     */
    private void scheduleFlush() {
        flushTask = getContext().getSystem().scheduler().scheduleWithFixedDelay(
                maxDelay,
                maxDelay,
                getSelf(),
                new Flush(),
                getContext().getDispatcher(),
                getSelf()
        );
    }

    /**
     * 重新调度刷新任务（例如在手动刷新后重置计时器）。
     */
    private void rescheduleFlush() {
        if (flushTask != null) {
            flushTask.cancel();
        }
        scheduleFlush();
    }
}
