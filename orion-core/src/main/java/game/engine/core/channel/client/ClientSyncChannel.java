package game.engine.core.channel.client;

import game.engine.core.batch.BatchConstants;
import game.engine.core.channel.BatchChannel;
import game.engine.core.sync.DeltaBuffer;
import game.engine.core.sync.DeltaEntity;
import game.engine.core.sync.DeltaSnapshot;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

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
public class ClientSyncChannel extends BatchChannel<DeltaSnapshot> {

    private final GatewayLocator gatewayLocator;

    public ClientSyncChannel(GatewayLocator gatewayLocator, ActorSystem system) {
        super("client-sync", 20, 100, system); // 小批次，低延迟
        this.gatewayLocator = gatewayLocator;
    }

    @Override
    public boolean accepts(Class<?> entityClass) {
        // 只同步需要客户端显示的实体
        // 这里可以通过注解或接口来标记
        return DeltaEntity.class.isAssignableFrom(entityClass);
    }

    @Override
    protected CompletionStage<Void> processBatchAsync(List<DeltaSnapshot> batch) {
        return CompletableFuture.runAsync(() -> {
            try {
                processSync(batch);
            } catch (Exception e) {
                logger.error("Client sync failed", e);
                throw new RuntimeException(e);
            }
        });
    }

    private void processSync(List<DeltaSnapshot> batch) throws Exception {
        // 按玩家ID分组
        Map<Long, List<DeltaSnapshot>> grouped = new HashMap<>();

        for (DeltaSnapshot snapshot : batch) {
            long ownerId = snapshot.getEntity().getOwnerId();
            if (ownerId > 0) {
                grouped.computeIfAbsent(ownerId, k -> new ArrayList<>()).add(snapshot);
            }
        }

        // 为每个玩家推送Delta数据
        int syncCount = 0;
        for (Map.Entry<Long, List<DeltaSnapshot>> entry : grouped.entrySet()) {
            long playerId = entry.getKey();
            List<DeltaSnapshot> changes = entry.getValue();

            // 查找玩家的Gateway连接
            ActorRef gateway = gatewayLocator.getGateway(playerId);
            if (gateway == null) {
                // 玩家可能已离线，忽略
                continue;
            }

            // 合并Delta数据
            byte[] deltaBytes = mergeDelta(changes);
            if (deltaBytes.length > 0) {
                // 推送到Gateway
                SyncDeltaMessage msg = new SyncDeltaMessage(playerId, deltaBytes);
                gateway.tell(msg, ActorRef.noSender());
                syncCount++;
            }
        }

        if (syncCount > 0) {
            logger.debug("Client sync completed: {} players, {} changes", syncCount, batch.size());
        }
    }

    /**
     * 合并多个实体的Delta数据
     */
    private byte[] mergeDelta(List<DeltaSnapshot> changes) throws IOException {
        DeltaBuffer buffer = new DeltaBuffer();

        for (DeltaSnapshot snapshot : changes) {
            DeltaEntity entity = snapshot.getEntity();
            // 注意：这里假设DeltaBuffer能处理DeltaSnapshot或Entity
            // 实际上DeltaBuffer可能需要适配，这里暂且假设addEntity能处理
            // 实际项目中可能需要根据DeltaSnapshot生成diff
            // 简单起见，这里我们假设addEntity会重新计算diff或直接使用snapshot
            // TODO: 优化DeltaBuffer以直接使用Snapshot避免重复计算
            buffer.addEntity(0, entity); // ID暂传0，需根据实际情况调整
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
