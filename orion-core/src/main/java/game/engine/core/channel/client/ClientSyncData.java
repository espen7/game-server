package game.engine.core.channel.client;

import game.engine.core.sync.DeltaSnapshot;

/**
 * 客户端同步数据包装类
 */
public class ClientSyncData {
    private final long playerId;
    private final int entityId;
    private final DeltaSnapshot snapshot;
    
    public ClientSyncData(long playerId, int entityId, DeltaSnapshot snapshot) {
        this.playerId = playerId;
        this.entityId = entityId;
        this.snapshot = snapshot;
    }
    
    public long getPlayerId() {
        return playerId;
    }
    
    public int getEntityId() {
        return entityId;
    }
    
    public DeltaSnapshot getSnapshot() {
        return snapshot;
    }
}
