package game.engine.gateway.rpc;

import game.engine.core.rpc.PekkoRpcClient;
import game.engine.core.rpc.RpcException;
import game.engine.core.rpc.RpcResponse;
import game.engine.core.rpc.ServiceInvoker;
import game.engine.core.rpc.client.EdgeServiceProvider;
import game.engine.core.rpc.internal.MeshServiceInvoker;
import game.engine.core.rpc.system.CoreServiceOrchestrator;
// 移除了特定服务的导入，使用通用RPC调用
import org.apache.pekko.actor.ActorSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * Gateway RPC调用者门面
 * 为Gateway提供统一的RPC调用接口
 */
public class GatewayRpcClient {
    private static final Logger logger = LoggerFactory.getLogger(GatewayRpcClient.class);
    
    private final EdgeServiceProvider edgeServiceProvider;      // 处理边缘服务请求
    private final MeshServiceInvoker meshServiceInvoker;      // 处理服务网格内调用
    private final CoreServiceOrchestrator infraOrchestrator;     // 处理基础设施调用
    private final String gatewayId;

    public GatewayRpcClient(ActorSystem actorSystem, String gatewayId) {
        this.gatewayId = gatewayId;
        this.edgeServiceProvider = new EdgeServiceProvider(actorSystem, "gateway-edge-" + gatewayId);
        this.meshServiceInvoker = new MeshServiceInvoker(actorSystem, "gateway-mesh-" + gatewayId);
        this.infraOrchestrator = new CoreServiceOrchestrator(actorSystem, "gateway-infra-" + gatewayId);
    }

    /**
     * 异步获取玩家信息 - 服务网格调用
     */
    public CompletableFuture<PlayerInfo> getPlayerInfoAsync(long playerId) {
        return meshServiceInvoker.callAsync("player-service", "getPlayerInfo", playerId)
                .thenApply(response -> {
                    if (response.isSuccess()) {
                        return (PlayerInfo) response.getResult();
                    } else {
                        logger.error("Failed to get player info for playerId {}: {}", 
                                   playerId, response.getException().getMessage());
                        throw new RuntimeException(response.getException());
                    }
                })
                .exceptionally(throwable -> {
                    logger.error("Exception while getting player info for playerId {}", playerId, throwable);
                    throw new RuntimeException(throwable);
                });
    }

    /**
     * 同步获取玩家信息 - 服务网格调用
     */
    public PlayerInfo getPlayerInfo(long playerId) throws RpcException {
        RpcResponse response = meshServiceInvoker.callSync("player-service", "getPlayerInfo", playerId);
        if (response.isSuccess()) {
            return (PlayerInfo) response.getResult();
        } else {
            throw response.getException();
        }
    }

    /**
     * 异步获取世界信息 - 服务网格调用
     */
    public CompletableFuture<Object> getWorldInfoAsync(int worldId) {
        return meshServiceInvoker.callAsync("world-service", "getWorldInfo", worldId)
                .thenApply(response -> {
                    if (response.isSuccess()) {
                        return response.getResult();
                    } else {
                        logger.error("Failed to get world info for worldId {}: {}", 
                                   worldId, response.getException().getMessage());
                        throw new RuntimeException(response.getException());
                    }
                })
                .exceptionally(throwable -> {
                    logger.error("Exception while getting world info for worldId {}", worldId, throwable);
                    throw new RuntimeException(throwable);
                });
    }

    /**
     * 玩家进入世界 - 服务网格调用
     */
    public boolean enterWorld(long playerId, int worldId) {
        try {
            RpcResponse response = meshServiceInvoker.callSync("world-service", "enterWorld", playerId, worldId);
            return response.isSuccess();
        } catch (RpcException e) {
            logger.error("Failed to enter world for playerId {} worldId {}: {}", 
                        playerId, worldId, e.getMessage());
            return false;
        }
    }

    /**
     * 发送系统消息给玩家 - 服务网格调用
     */
    public boolean sendSystemMessage(long playerId, String message) {
        try {
            RpcResponse response = meshServiceInvoker.callSync("player-service", "sendSystemMessage", playerId, message);
            return response.isSuccess();
        } catch (RpcException e) {
            logger.error("Failed to send system message to playerId {}: {}", playerId, e.getMessage());
            return false;
        }
    }

    /**
     * 广播消息到世界 - 服务网格调用
     */
    public boolean broadcastToWorld(int worldId, String message) {
        try {
            RpcResponse response = meshServiceInvoker.callSync("world-service", "broadcastToWorld", worldId, message);
            return response.isSuccess();
        } catch (RpcException e) {
            logger.error("Failed to broadcast message to worldId {}: {}", worldId, e.getMessage());
            return false;
        }
    }

    /**
     * 检查玩家在线状态 - 服务网格调用
     */
    public boolean isPlayerOnline(long playerId) {
        try {
            RpcResponse response = meshServiceInvoker.callSync("player-service", "isPlayerOnline", playerId);
            if (response.isSuccess()) {
                return (Boolean) response.getResult();
            }
            return false;
        } catch (RpcException e) {
            logger.warn("Failed to check player online status for playerId {}: {}", playerId, e.getMessage());
            return false;
        }
    }

    /**
     * 获取世界在线玩家数量 - 服务网格调用
     */
    public int getWorldPlayerCount(int worldId) {
        try {
            RpcResponse response = meshServiceInvoker.callSync("world-service", "getOnlinePlayerCount", worldId);
            if (response.isSuccess()) {
                return (Integer) response.getResult();
            }
            return 0;
        } catch (RpcException e) {
            logger.warn("Failed to get world player count for worldId {}: {}", worldId, e.getMessage());
            return 0;
        }
    }

    /**
     * 处理来自边缘客户端的RPC请求
     */
    public CompletableFuture<RpcResponse> handleEdgeRequest(String serviceName, String methodName, Object... params) {
        return edgeServiceProvider.callAsync(serviceName, methodName, params);
    }
    
    /**
     * 处理基础设施相关调用
     */
    public CompletableFuture<RpcResponse> handleInfraRequest(String serviceName, String methodName, Object... params) {
        return infraOrchestrator.callAsync(serviceName, methodName, params);
    }

    public void close() {
        edgeServiceProvider.close();
        // meshServiceInvoker和infraOrchestrator不需要显式关闭
    }

    /**
     * 玩家信息DTO
     */
    public static class PlayerInfo {
        private final long playerId;
        private final String nickname;
        private final int level;
        private final boolean online;

        public PlayerInfo(long playerId, String nickname, int level, boolean online) {
            this.playerId = playerId;
            this.nickname = nickname;
            this.level = level;
            this.online = online;
        }

        // Getters
        public long getPlayerId() { return playerId; }
        public String getNickname() { return nickname; }
        public int getLevel() { return level; }
        public boolean isOnline() { return online; }

        @Override
        public String toString() {
            return String.format("PlayerInfo{id=%d, name=%s, level=%d, online=%s}", 
                               playerId, nickname, level, online);
        }
    }
}