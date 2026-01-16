package game.engine.player.rpc;

import game.engine.core.rpc.RpcException;
import game.engine.core.rpc.ServiceProvider;
import game.engine.player.entity.Player;

import java.util.concurrent.CompletableFuture;

/**
 * Player服务RPC接口
 * 定义Player模块对外提供的RPC方法
 */
public interface PlayerRpcService extends ServiceProvider {
    
    /**
     * 获取玩家信息
     * @param playerId 玩家ID
     * @return Player对象
     * @throws RpcException 调用失败时抛出
     */
    Player getPlayerInfo(long playerId) throws RpcException;
    
    /**
     * 异步获取玩家信息
     * @param playerId 玩家ID
     * @return CompletableFuture<Player>
     */
    CompletableFuture<Player> getPlayerInfoAsync(long playerId);
    
    /**
     * 更新玩家等级
     * @param playerId 玩家ID
     * @param newLevel 新等级
     * @return 是否更新成功
     * @throws RpcException 调用失败时抛出
     */
    boolean updatePlayerLevel(long playerId, int newLevel) throws RpcException;
    
    /**
     * 获取玩家在线状态
     * @param playerId 玩家ID
     * @return true表示在线
     * @throws RpcException 调用失败时抛出
     */
    boolean isPlayerOnline(long playerId) throws RpcException;
    
    /**
     * 发送系统消息给玩家
     * @param playerId 玩家ID
     * @param message 消息内容
     * @return 是否发送成功
     * @throws RpcException 调用失败时抛出
     */
    boolean sendSystemMessage(long playerId, String message) throws RpcException;
}