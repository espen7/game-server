package game.engine.core.persistence;

import game.engine.core.persistence.mybatis.MyBatisConfig;
import game.engine.core.sync.DeltaEntity;
import game.engine.core.sync.DeltaSnapshot;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * 批处理数据写入器（改进版）。
 * 负责接收脏实体，缓冲并批量写入数据库。
 * 
 * 改进点：
 * 1. 使用虚拟线程替代传统线程
 * 2. 使用快照机制解决并发安全问题
 * 3. 添加错误重试和死信队列
 * 4. 支持优雅关闭
 * 
 * @deprecated 请使用新的通道系统 {@link game.engine.core.channel.DeltaPublisher}
 * 和 {@link game.engine.core.channel.database.DatabaseChannel}
 * 
 * 迁移示例：
 * <pre>
 * // 旧方式
 * BatchDataWriter.getInstance().submit(player);
 * 
 * // 新方式
 * DeltaPublisher.getInstance().publish(player);
 * // 或选择性发布
 * DeltaPublisher.getInstance().publishTo(player, "database");
 * </pre>
 */
@Deprecated
public class BatchDataWriter {
    private static final Logger logger = LoggerFactory.getLogger(BatchDataWriter.class);
    private static final BatchDataWriter INSTANCE = new BatchDataWriter();

    private final BlockingQueue<DeltaSnapshot> queue = new LinkedBlockingQueue<>();
    private final BlockingQueue<DeltaSnapshot> deadLetterQueue = new LinkedBlockingQueue<>();
    private final ExecutorService executor;
    private volatile boolean running = true;

    // 批处理配置
    private static final int BATCH_SIZE = 100;
    private static final long FLUSH_INTERVAL_MS = 5000;
    private static final int MAX_RETRY = 3;
    private static final long RETRY_DELAY_MS = 1000;

    private BatchDataWriter() {
        // 使用虚拟线程执行器
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        // 启动批处理工作线程（虚拟线程）
        executor.submit(this::processLoop);
        logger.info("BatchDataWriter initialized with virtual threads");
    }

    public static BatchDataWriter getInstance() {
        return INSTANCE;
    }

    /**
     * 提交需要保存的实体（改进版）。
     * 
     * @param entity 脏实体
     */
    public void submit(DeltaEntity entity) {
        if (entity.isDirty() || entity.getState() == DeltaEntity.State.TRANSIENT) {
            // 创建快照
            DeltaSnapshot snapshot = new DeltaSnapshot(entity);
            queue.offer(snapshot);
            // 立即清除脏标记，允许实体继续被修改
            entity.clearDirty();
        }
    }

    private void processLoop() {
        List<DeltaSnapshot> batch = new ArrayList<>(BATCH_SIZE);
        long lastFlushTime = System.currentTimeMillis();

        while (running) {
            try {
                DeltaSnapshot snapshot = queue.poll(100, TimeUnit.MILLISECONDS);
                if (snapshot != null) {
                    batch.add(snapshot);
                }

                long now = System.currentTimeMillis();
                if (!batch.isEmpty() && (batch.size() >= BATCH_SIZE || now - lastFlushTime >= FLUSH_INTERVAL_MS)) {
                    flushWithRetry(batch);
                    batch.clear();
                    lastFlushTime = now;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("ProcessLoop interrupted");
                break;
            } catch (Exception e) {
                logger.error("Error in batch writer loop", e);
            }
        }
        
        // 关闭前处理剩余数据
        if (!batch.isEmpty()) {
            logger.info("Flushing remaining {} entities before shutdown", batch.size());
            flushWithRetry(batch);
        }
    }

    /**
     * 带重试的刷新
     */
    private void flushWithRetry(List<DeltaSnapshot> batch) {
        int attempt = 0;
        while (attempt < MAX_RETRY) {
            try {
                flush(batch);
                return; // 成功
            } catch (Exception e) {
                attempt++;
                logger.warn("Flush failed (attempt {}/{}): {}", attempt, MAX_RETRY, e.getMessage());
                if (attempt >= MAX_RETRY) {
                    logger.error("Failed to flush after {} retries, moving to dead letter queue", MAX_RETRY, e);
                    deadLetterQueue.addAll(batch);
                    // TODO: 实现死信队列处理逻辑（告警、持久化到文件等）
                    break;
                }
                // 重试前等待
                try {
                    Thread.sleep(RETRY_DELAY_MS * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
    private void flush(List<DeltaSnapshot> batch) {
        SqlSessionFactory sqlSessionFactory = MyBatisConfig.getInstance().getSqlSessionFactory();
        // 开启 BATCH 模式
        try (SqlSession session = sqlSessionFactory.openSession(ExecutorType.BATCH, false)) {
            for (DeltaSnapshot snapshot : batch) {
                DeltaEntity entity = snapshot.getEntity();
                
                // 根据状态决定INSERT还是UPDATE
                String statement;
                if (snapshot.isInsert()) {
                    statement = entity.getClass().getName() + "Mapper.insert";
                    session.insert(statement, entity);
                } else if (snapshot.isUpdate()) {
                    statement = entity.getClass().getName() + "Mapper.update";
                    session.update(statement, entity);
                } else {
                    // 既不是INSERT也不是UPDATE，跳过
                    continue;
                }
            }
            session.commit();

            // 成功后更新实体状态
            for (DeltaSnapshot snapshot : batch) {
                DeltaEntity entity = snapshot.getEntity();
                if (snapshot.isInsert()) {
                    entity.onPersisted(); // TRANSIENT -> MANAGED
                }
                // UPDATE的实体已经是MANAGED状态，不需要改变
            }

            logger.info("Flushed {} entities to database", batch.size());
        } catch (Exception e) {
            logger.error("Failed to flush batch", e);
            throw new RuntimeException("Flush failed", e);
        }
    }

    /**
     * 优雅关闭
     */
    public void shutdown() {
        logger.info("Shutting down BatchDataWriter...");
        running = false;
        
        try {
            executor.shutdown();
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warn("Executor did not terminate in time, forcing shutdown");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
        
        logger.info("BatchDataWriter shutdown complete. Dead letter queue size: {}", deadLetterQueue.size());
    }
    
    /**
     * 获取死信队列大小（用于监控）
     */
    public int getDeadLetterQueueSize() {
        return deadLetterQueue.size();
    }
    
    /**
     * 获取待处理队列大小（用于监控）
     */
    public int getPendingQueueSize() {
        return queue.size();
    }
}
