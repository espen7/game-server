package game.engine.player.rpc;

import game.engine.core.rpc.*;
import game.engine.player.actor.PlayerActor;
import game.engine.player.entity.Player;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.pattern.Patterns;
import org.apache.pekko.util.Timeout;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Player RPC服务实现
 * 基于Pekko Cluster Sharding实现Player服务的RPC调用
 */
public class PlayerRpcServiceImpl implements PlayerRpcService {
    private final ActorSystem actorSystem;
    private final String serviceName = "player-service";

    public PlayerRpcServiceImpl(ActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    @Override
    public String getServiceName() {
        return serviceName;
    }

    @Override
    public Player getPlayerInfo(long playerId) throws RpcException {
        try {
            ActorRef playerActor = getPlayerActor(playerId);
            Timeout timeout = Timeout.apply(3, TimeUnit.SECONDS);
            
            // 发送获取玩家信息的消息
            Future<Object> future = Patterns.ask(playerActor, 
                new PlayerActor.GetPlayerInfo(), timeout);
            
            Object result = Await.result(future, Duration.apply(3, TimeUnit.SECONDS));
            
            if (result instanceof PlayerActor.PlayerInfoResponse) {
                PlayerActor.PlayerInfoResponse response = (PlayerActor.PlayerInfoResponse) result;
                if (response.isSuccess()) {
                    return response.getPlayer();
                } else {
                    throw new RpcException(RpcException.RpcErrorType.INTERNAL_ERROR,
                                         "Failed to get player info: " + response.getError());
                }
            } else {
                throw new RpcException(RpcException.RpcErrorType.INTERNAL_ERROR,
                                     "Unexpected response type: " + result.getClass());
            }
        } catch (Exception e) {
            throw new RpcException(RpcException.RpcErrorType.INTERNAL_ERROR,
                                 "getPlayerInfo failed: " + e.getMessage(), null, e);
        }
    }

    @Override
    public CompletableFuture<Player> getPlayerInfoAsync(long playerId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return getPlayerInfo(playerId);
            } catch (RpcException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public boolean updatePlayerLevel(long playerId, int newLevel) throws RpcException {
        try {
            ActorRef playerActor = getPlayerActor(playerId);
            Timeout timeout = Timeout.apply(3, TimeUnit.SECONDS);
            
            Future<Object> future = Patterns.ask(playerActor,
                new PlayerActor.UpdateLevelCommand(newLevel), timeout);
            
            Object result = Await.result(future, Duration.apply(3, TimeUnit.SECONDS));
            
            if (result instanceof PlayerActor.CommandResult) {
                PlayerActor.CommandResult response = (PlayerActor.CommandResult) result;
                return response.isSuccess();
            } else {
                throw new RpcException(RpcException.RpcErrorType.INTERNAL_ERROR,
                                     "Unexpected response type: " + result.getClass());
            }
        } catch (Exception e) {
            throw new RpcException(RpcException.RpcErrorType.INTERNAL_ERROR,
                                 "updatePlayerLevel failed: " + e.getMessage(), null, e);
        }
    }

    @Override
    public boolean isPlayerOnline(long playerId) throws RpcException {
        try {
            ActorRef playerActor = getPlayerActor(playerId);
            Timeout timeout = Timeout.apply(2, TimeUnit.SECONDS);
            
            Future<Object> future = Patterns.ask(playerActor,
                new PlayerActor.CheckOnlineStatus(), timeout);
            
            Object result = Await.result(future, Duration.apply(2, TimeUnit.SECONDS));
            
            if (result instanceof PlayerActor.OnlineStatusResponse) {
                return ((PlayerActor.OnlineStatusResponse) result).isOnline();
            } else {
                throw new RpcException(RpcException.RpcErrorType.INTERNAL_ERROR,
                                     "Unexpected response type: " + result.getClass());
            }
        } catch (Exception e) {
            // 如果找不到玩家Actor，则认为不在线
            return false;
        }
    }

    @Override
    public boolean sendSystemMessage(long playerId, String message) throws RpcException {
        try {
            ActorRef playerActor = getPlayerActor(playerId);
            Timeout timeout = Timeout.apply(3, TimeUnit.SECONDS);
            
            Future<Object> future = Patterns.ask(playerActor,
                new PlayerActor.SendSystemMessage(message), timeout);
            
            Object result = Await.result(future, Duration.apply(3, TimeUnit.SECONDS));
            
            if (result instanceof PlayerActor.CommandResult) {
                return ((PlayerActor.CommandResult) result).isSuccess();
            } else {
                throw new RpcException(RpcException.RpcErrorType.INTERNAL_ERROR,
                                     "Unexpected response type: " + result.getClass());
            }
        } catch (Exception e) {
            throw new RpcException(RpcException.RpcErrorType.INTERNAL_ERROR,
                                 "sendSystemMessage failed: " + e.getMessage(), null, e);
        }
    }

    @Override
    public Object invokeMethod(String methodName, Object... parameters) throws RpcException {
        switch (methodName) {
            case "getPlayerInfo":
                if (parameters.length != 1 || !(parameters[0] instanceof Long)) {
                    throw new RpcException(RpcException.RpcErrorType.INVALID_PARAMETERS,
                                         "getPlayerInfo requires single Long parameter");
                }
                return getPlayerInfo((Long) parameters[0]);
                
            case "updatePlayerLevel":
                if (parameters.length != 2 || 
                    !(parameters[0] instanceof Long) || 
                    !(parameters[1] instanceof Integer)) {
                    throw new RpcException(RpcException.RpcErrorType.INVALID_PARAMETERS,
                                         "updatePlayerLevel requires (Long, Integer) parameters");
                }
                return updatePlayerLevel((Long) parameters[0], (Integer) parameters[1]);
                
            case "isPlayerOnline":
                if (parameters.length != 1 || !(parameters[0] instanceof Long)) {
                    throw new RpcException(RpcException.RpcErrorType.INVALID_PARAMETERS,
                                         "isPlayerOnline requires single Long parameter");
                }
                return isPlayerOnline((Long) parameters[0]);
                
            case "sendSystemMessage":
                if (parameters.length != 2 || 
                    !(parameters[0] instanceof Long) || 
                    !(parameters[1] instanceof String)) {
                    throw new RpcException(RpcException.RpcErrorType.INVALID_PARAMETERS,
                                         "sendSystemMessage requires (Long, String) parameters");
                }
                return sendSystemMessage((Long) parameters[0], (String) parameters[1]);
                
            default:
                throw new RpcException(RpcException.RpcErrorType.METHOD_NOT_FOUND,
                                     "Method not found: " + methodName);
        }
    }

    @Override
    public boolean isAvailable() {
        // Player服务总是可用的（基于Sharding）
        return true;
    }

    @Override
    public String getHealthStatus() {
        return "healthy";
    }

    /**
     * 根据玩家ID获取对应的PlayerActor引用
     */
    private ActorRef getPlayerActor(long playerId) throws Exception {
        // 使用Cluster Sharding获取PlayerActor
        scala.concurrent.Future<ActorRef> future = actorSystem.actorSelection("/user/player-shard-region/" + playerId)
            .resolveOne(Duration.create(2, TimeUnit.SECONDS));
        return Await.result(future, Duration.apply(2, TimeUnit.SECONDS));
    }
}