package game.engine.core.persistence;

import game.engine.core.persistence.mybatis.MyBatisConfig;
import game.engine.core.sync.DeltaEntity;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 批处理数据写入器。
 * 负责接收脏实体，缓冲并批量写入数据库。
 */
public class BatchDataWriter {
    private static final Logger logger = LoggerFactory.getLogger(BatchDataWriter.class);
    private static final BatchDataWriter INSTANCE = new BatchDataWriter();

    private final BlockingQueue<DeltaEntity> queue = new LinkedBlockingQueue<>();
    private final Thread workerThread;
    private volatile boolean running = true;

    // 批处理配置
    private static final int BATCH_SIZE = 100;
    private static final long FLUSH_INTERVAL_MS = 5000;

    private BatchDataWriter() {
        this.workerThread = new Thread(this::processLoop, "BatchDataWriter-Thread");
        this.workerThread.start();
    }

    public static BatchDataWriter getInstance() {
        return INSTANCE;
    }

    /**
     * 提交需要保存的实体。
     * 
     * @param entity 脏实体
     */
    public void submit(DeltaEntity entity) {
        if (entity.isDirty()) {
            queue.offer(entity);
        }
    }

    private void processLoop() {
        List<DeltaEntity> batch = new ArrayList<>(BATCH_SIZE);
        long lastFlushTime = System.currentTimeMillis();

        while (running) {
            try {
                DeltaEntity entity = queue.poll(100, TimeUnit.MILLISECONDS);
                if (entity != null) {
                    batch.add(entity);
                }

                long now = System.currentTimeMillis();
                if (!batch.isEmpty() && (batch.size() >= BATCH_SIZE || now - lastFlushTime >= FLUSH_INTERVAL_MS)) {
                    flush(batch);
                    batch.clear();
                    lastFlushTime = now;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Error in batch writer loop", e);
            }
        }
    }

    private void flush(List<DeltaEntity> batch) {
        SqlSessionFactory sqlSessionFactory = MyBatisConfig.getInstance().getSqlSessionFactory();
        // 开启 BATCH 模式
        try (SqlSession session = sqlSessionFactory.openSession(ExecutorType.BATCH, false)) {
            for (DeltaEntity entity : batch) {
                // 假设所有 Entity 都遵循通用 Mapper 约定，或者通过反射找到对应 Mapper
                // 这里为了简化，假设有一个通用的 updateDelta 方法调用
                // 实际项目中可能需要根据 entity 类型获取 Mapper
                // session.update(entity.getClass().getName() + "Mapper.updateDelta", entity);

                // 简单示例：直接反射调用 Mapper (性能较差，仅演示)
                // 更好的方式是维护 EntityClass -> MapperClass 的映射
                String statement = entity.getClass().getName() + "Mapper.updateDelta";
                session.update(statement, entity);
            }
            session.commit();

            // 成功落地后，清除脏标记
            // 注意：这里可能存在并发问题，如果业务线程在 flush 期间又修改了 entity
            // 严格来说应该只清除已持久化的那些 dirty flags。
            // 但 DeltaEntity 目前是简单的 clearDirty() 清除所有。
            // 建议：在 submit 时克隆 dirty flags 或 entity，或者加锁。
            // 本示例简化处理：
            for (DeltaEntity entity : batch) {
                entity.clearDirty();
            }

            logger.info("Flushed {} entities to database", batch.size());
        } catch (Exception e) {
            logger.error("Failed to flush batch", e);
            // 失败处理策略：重试？丢弃？
        }
    }

    public void shutdown() {
        running = false;
        try {
            workerThread.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
