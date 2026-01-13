package game.engine.world.rpc;

import game.engine.core.rpc.RpcException;
import game.engine.core.rpc.RpcService;

import java.util.concurrent.CompletableFuture;

/**
 * World服务RPC接口
 * 定义World模块对外提供的RPC方法
 */
public interface WorldRpcService extends RpcService {
    
    /**
     * 获取世界信息
     * @param worldId 世界ID
     * @return 世界信息
     * @throws RpcException 调用失败时抛出
     */
    WorldInfo getWorldInfo(int worldId) throws RpcException;
    
    /**
     * 异步获取世界信息
     * @param worldId 世界ID
     * @return CompletableFuture<WorldInfo>
     */
    CompletableFuture<WorldInfo> getWorldInfoAsync(int worldId);
    
    /**
     * 玩家进入世界
     * @param playerId 玩家ID
     * @param worldId 世界ID
     * @return 是否成功进入
     * @throws RpcException 调用失败时抛出
     */
    boolean enterWorld(long playerId, int worldId) throws RpcException;
    
    /**
     * 玩家离开世界
     * @param playerId 玩家ID
     * @param worldId 世界ID
     * @return 是否成功离开
     * @throws RpcException 调用失败时抛出
     */
    boolean leaveWorld(long playerId, int worldId) throws RpcException;
    
    /**
     * 广播消息到世界
     * @param worldId 世界ID
     * @param message 消息内容
     * @return 是否广播成功
     * @throws RpcException 调用失败时抛出
     */
    boolean broadcastToWorld(int worldId, String message) throws RpcException;
    
    /**
     * 获取世界在线玩家数量
     * @param worldId 世界ID
     * @return 在线玩家数量
     * @throws RpcException 调用失败时抛出
     */
    int getOnlinePlayerCount(int worldId) throws RpcException;
}