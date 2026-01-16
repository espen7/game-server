package game.engine.core.persistence.channel;

import game.engine.core.sync.DeltaBuffer;
import game.engine.core.sync.DeltaEntity;
import game.engine.core.sync.DeltaSnapshot;
import org.apache.pekko.actor.ActorRef;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户端增量同步通道。
 * 
 * 职责：
 * 1. 将实体变更打包成Delta数据
 * 2. 按玩家分组，推送到对应的Gateway连接
 * 3. 支持高频低延迟同步
 * 
 * 特点：
 * - 小批次（20）快速同步
 * - 低延迟（100ms刷新间隔）
 * - 自动合并同一玩家的多个变更
 */
public class ClientSyncChannel extends BatchChannel<ClientSyncData> {
    
    private final GatewayLocator gatewayLocator;
    
    public ClientSyncChannel(GatewayLocator gatewayLocator) {
        super("client-sync", 20, 100);  // 小批次，低延迟
        this.gatewayLocator = gatewayLocator;
    }
    
    @Override
    public boolean accepts(Class<?> entityClass) {
        // 只同步需要客户端显示的实体
        // 这里可以通过注解或接口来标记
        return DeltaEntity.class.isAssignableFrom(entityClass);
    }
    
    @Override
    protected void processBatch(List<ClientSyncData> batch) throws Exception {
        // 按玩家ID分组
        Map<Long, List<ClientSyncData>> grouped = new HashMap<>();
        for (ClientSyncData data : batch) {
            grouped.computeIfAbsent(data.getPlayerId(), k -> new ArrayList<>())
                   .add(data);
        }
        
        // 为每个玩家推送Delta数据
        int syncCount = 0;
        for (Map.Entry<Long, List<ClientSyncData>> entry : grouped.entrySet()) {
            long playerId = entry.getKey();
            List<ClientSyncData> changes = entry.getValue();
            
            // 查找玩家的Gateway连接
            ActorRef gateway = gatewayLocator.getGateway(playerId);
            if (gateway == null) {
                logger.warn("Gateway not found for player: {}", playerId);
                continue;
            }
            
            // 合并Delta数据
            byte[] deltaBytes = mergeDelta(changes);
            
            // 推送到Gateway
            SyncDeltaMessage msg = new SyncDeltaMessage(playerId, deltaBytes);
            gateway.tell(msg, ActorRef.noSender());
            
            syncCount++;
        }
        
        logger.info("Client sync completed: {} players, {} changes", syncCount, batch.size());
    }
    
    /**
     * 合并多个实体的Delta数据
     */
    private byte[] mergeDelta(List<ClientSyncData> changes) throws IOException {
        DeltaBuffer buffer = new DeltaBuffer();
        
        for (ClientSyncData data : changes) {
            DeltaSnapshot snapshot = data.getSnapshot();
            DeltaEntity entity = snapshot.getEntity();
            
            // 添加实体ID和Delta数据
            buffer.addEntity(data.getEntityId(), entity);
        }
        
        return buffer.toBytes();
    }
    
    /**
     * Gateway定位器接口
     */
    public interface GatewayLocator {
        /**
         * 获取玩家对应的Gateway Actor
         */
        ActorRef getGateway(long playerId);
    }
    
    /**
     * 同步Delta消息
     */
    public static class SyncDeltaMessage {
        private final long playerId;
        private final byte[] deltaData;
        
        public SyncDeltaMessage(long playerId, byte[] deltaData) {
            this.playerId = playerId;
            this.deltaData = deltaData;
        }
        
        public long getPlayerId() {
            return playerId;
        }
        
        public byte[] getDeltaData() {
            return deltaData;
        }
    }
}
