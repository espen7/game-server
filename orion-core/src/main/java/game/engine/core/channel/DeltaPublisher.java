package game.engine.core.channel;

import game.engine.core.sync.DeltaEntity;
import game.engine.core.sync.DeltaSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Delta变更发布者 - 管理所有批处理通道。
 * 
 * 职责：
 * 1. 管理所有批处理通道的注册和生命周期
 * 2. 将实体变更分发到订阅的通道
 * 3. 支持选择性发布和全量广播
 * 
 * 使用示例：
 * <pre>
 * // 注册通道
 * publisher.registerChannel("database", new DatabaseChannel());
 * publisher.registerChannel("redis", new RedisChannel());
 * 
 * // 发布变更
 * publisher.publish(player);  // 广播到所有通道
 * publisher.publishTo(player, "database", "redis");  // 选择性发布
 * </pre>
 */
public class DeltaPublisher {
    private static final Logger logger = LoggerFactory.getLogger(DeltaPublisher.class);
    private static final DeltaPublisher INSTANCE = new DeltaPublisher();
    
    private final Map<String, BatchChannel<?>> channels = new ConcurrentHashMap<>();
    
    private DeltaPublisher() {
        logger.info("DeltaPublisher initialized");
    }
    
    public static DeltaPublisher getInstance() {
        return INSTANCE;
    }
    
    /**
     * 注册批处理通道
     * 
     * @param channelName 通道名称（唯一标识）
     * @param channel 批处理通道实例
     */
    public void registerChannel(String channelName, BatchChannel<?> channel) {
        BatchChannel<?> old = channels.put(channelName, channel);
        if (old != null) {
            logger.warn("Channel [{}] already exists, replaced with new instance", channelName);
            old.shutdown();
        }
        logger.info("Channel [{}] registered: {}", channelName, channel.getClass().getSimpleName());
    }
    
    /**
     * 取消注册通道
     */
    public void unregisterChannel(String channelName) {
        BatchChannel<?> channel = channels.remove(channelName);
        if (channel != null) {
            channel.shutdown();
            logger.info("Channel [{}] unregistered", channelName);
        }
    }
    
    /**
     * 发布实体变更到所有订阅该实体类型的通道
     * 
     * @param entity 变更的实体
     */
    public void publish(DeltaEntity entity) {
        if (!entity.isDirty() && entity.getState() != DeltaEntity.State.TRANSIENT) {
            // 没有变更，跳过
            return;
        }
        
        // 创建快照
        DeltaSnapshot snapshot = new DeltaSnapshot(entity);
        // 立即清除脏标记，允许实体继续被修改
        entity.clearDirty();
        
        Class<?> entityClass = entity.getClass();
        int publishedCount = 0;
        
        // 广播到所有接受该实体类型的通道
        for (Map.Entry<String, BatchChannel<?>> entry : channels.entrySet()) {
            BatchChannel<?> channel = entry.getValue();
            if (channel.accepts(entityClass)) {
                submitToChannel(channel, snapshot);
                publishedCount++;
            }
        }
        
        if (publishedCount == 0) {
            logger.warn("No channel accepts entity: {}", entityClass.getSimpleName());
        } else {
            logger.debug("Published {} to {} channels", entityClass.getSimpleName(), publishedCount);
        }
    }
    
    /**
     * 发布到指定的通道
     * 
     * @param entity 变更的实体
     * @param channelNames 目标通道名称列表
     */
    public void publishTo(DeltaEntity entity, String... channelNames) {
        if (channelNames == null || channelNames.length == 0) {
            logger.warn("No channel names specified, skipping publish");
            return;
        }
        
        if (!entity.isDirty() && entity.getState() != DeltaEntity.State.TRANSIENT) {
            return;
        }
        
        // 创建快照
        DeltaSnapshot snapshot = new DeltaSnapshot(entity);
        entity.clearDirty();
        
        for (String channelName : channelNames) {
            BatchChannel<?> channel = channels.get(channelName);
            if (channel == null) {
                logger.warn("Channel [{}] not found", channelName);
                continue;
            }
            
            if (!channel.accepts(entity.getClass())) {
                logger.warn("Channel [{}] does not accept entity: {}", 
                    channelName, entity.getClass().getSimpleName());
                continue;
            }
            
            submitToChannel(channel, snapshot);
        }
    }
    
    /**
     * 提交快照到通道（类型安全处理）
     */
    @SuppressWarnings("unchecked")
    private void submitToChannel(BatchChannel<?> channel, DeltaSnapshot snapshot) {
        try {
            ((BatchChannel<DeltaSnapshot>) channel).submit(snapshot);
        } catch (ClassCastException e) {
            logger.error("Type mismatch when submitting to channel", e);
        }
    }
    
    /**
     * 获取所有通道名称
     */
    public Set<String> getChannelNames() {
        return channels.keySet();
    }
    
    /**
     * 获取指定通道
     */
    public BatchChannel<?> getChannel(String channelName) {
        return channels.get(channelName);
    }
    
    /**
     * 获取所有通道的统计信息
     */
    public Map<String, ChannelMetrics> getAllMetrics() {
        Map<String, ChannelMetrics> metricsMap = new ConcurrentHashMap<>();
        for (Map.Entry<String, BatchChannel<?>> entry : channels.entrySet()) {
            metricsMap.put(entry.getKey(), entry.getValue().getMetrics());
        }
        return metricsMap;
    }
    
    /**
     * 关闭所有通道
     */
    public void shutdown() {
        logger.info("Shutting down DeltaPublisher with {} channels", channels.size());
        for (Map.Entry<String, BatchChannel<?>> entry : channels.entrySet()) {
            try {
                entry.getValue().shutdown();
                logger.info("Channel [{}] shut down", entry.getKey());
            } catch (Exception e) {
                logger.error("Error shutting down channel [{}]", entry.getKey(), e);
            }
        }
        channels.clear();
    }
}
