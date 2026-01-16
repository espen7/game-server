package game.engine.core.channel.database;

import game.engine.core.channel.BatchChannel;
import game.engine.core.persistence.mybatis.MyBatisConfig;
import game.engine.core.sync.DeltaEntity;
import game.engine.core.sync.DeltaSnapshot;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.pekko.actor.ActorSystem;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 数据库持久化通道。
 * 
 * 职责：
 * 1. 批量持久化实体到数据库
 * 2. 根据实体状态自动判断INSERT/UPDATE
 * 3. 失败时保存到本地文件作为备份
 */
public class DatabaseChannel extends BatchChannel<DeltaSnapshot> {
    
    private static final String DEAD_LETTER_DIR = "dead_letter/database";
    
    public DatabaseChannel(ActorSystem system) {
        super("database", 100, 5000, system);
        initDeadLetterDir();
    }
    
    public DatabaseChannel(int batchSize, long flushIntervalMs, ActorSystem system) {
        super("database", batchSize, flushIntervalMs, system);
        initDeadLetterDir();
    }
    
    private void initDeadLetterDir() {
        try {
            Path dir = Paths.get(DEAD_LETTER_DIR);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
        } catch (IOException e) {
            logger.error("Failed to create dead letter directory", e);
        }
    }
    
    @Override
    public boolean accepts(Class<?> entityClass) {
        // 所有继承DeltaEntity的实体都需要持久化
        return DeltaEntity.class.isAssignableFrom(entityClass);
    }
    
    @Override
    protected void processBatch(List<DeltaSnapshot> batch) throws Exception {
        SqlSessionFactory factory = MyBatisConfig.getInstance().getSqlSessionFactory();
        
        try (SqlSession session = factory.openSession(ExecutorType.BATCH, false)) {
            int insertCount = 0;
            int updateCount = 0;
            
            for (DeltaSnapshot snapshot : batch) {
                DeltaEntity entity = snapshot.getEntity();
                String mapperName = entity.getClass().getName() + "Mapper";
                
                if (snapshot.isInsert()) {
                    // INSERT操作
                    String statement = mapperName + ".insert";
                    session.insert(statement, entity);
                    insertCount++;
                } else if (snapshot.isUpdate()) {
                    // UPDATE操作
                    String statement = mapperName + ".update";
                    session.update(statement, entity);
                    updateCount++;
                }
            }
            
            // 提交事务
            session.commit();
            
            // 更新实体状态
            for (DeltaSnapshot snapshot : batch) {
                if (snapshot.isInsert()) {
                    snapshot.getEntity().onPersisted();
                }
            }
            
            logger.info("Database batch processed: {} inserts, {} updates", insertCount, updateCount);
        }
    }
    
    @Override
    protected void onProcessFailed(List<DeltaSnapshot> batch, Exception e) {
        super.onProcessFailed(batch, e);
        
        // 保存到死信文件
        try {
            saveToDeadLetter(batch, e);
        } catch (IOException ioe) {
            logger.error("Failed to save dead letter", ioe);
        }
    }
    
    /**
     * 保存失败的批次到文件
     */
    private void saveToDeadLetter(List<DeltaSnapshot> batch, Exception cause) throws IOException {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = String.format("%s/batch_%s.txt", DEAD_LETTER_DIR, timestamp);
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.println("=== Database Batch Failed ===");
            writer.println("Time: " + LocalDateTime.now());
            writer.println("Batch Size: " + batch.size());
            writer.println("Error: " + cause.getMessage());
            writer.println();
            
            for (int i = 0; i < batch.size(); i++) {
                DeltaSnapshot snapshot = batch.get(i);
                DeltaEntity entity = snapshot.getEntity();
                
                writer.println("--- Entity " + (i + 1) + " ---");
                writer.println("Type: " + entity.getClass().getSimpleName());
                writer.println("State: " + snapshot.getState());
                writer.println("Version: " + snapshot.getVersion());
                writer.println("Entity: " + entity.toString());
                writer.println();
            }
        }
        
        logger.info("Dead letter saved to: {}", filename);
    }
}
