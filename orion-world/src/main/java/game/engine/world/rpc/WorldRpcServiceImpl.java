package game.engine.world.rpc;

import game.engine.core.actor.WorldMessages;
import game.engine.core.OrionServices;
import game.engine.core.rpc.RpcException;
import game.engine.core.rpc.RpcService;
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
 * World RPC服务实现
 * 基于WorldServiceActor提供RPC功能
 */
public class WorldRpcServiceImpl implements WorldRpcService {
    private final ActorSystem actorSystem;
    private final String serviceName = "world-service";

    public WorldRpcServiceImpl(ActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    @Override
    public String getServiceName() {
        return serviceName;
    }

    @Override
    public WorldInfo getWorldInfo(int worldId) throws RpcException {
        try {
            ActorRef worldActor = getWorldActor(worldId);
            Timeout timeout = Timeout.apply(3, TimeUnit.SECONDS);
            
            Future<Object> future = Patterns.ask(worldActor,
                new GetWorldInfoMessage(), timeout);
            
            Object result = Await.result(future, Duration.apply(3, TimeUnit.SECONDS));
            
            if (result instanceof WorldInfoResponse) {
                WorldInfoResponse response = (WorldInfoResponse) result;
                if (response.isSuccess()) {
                    return response.getWorldInfo();
                } else {
                    throw new RpcException(RpcException.RpcErrorType.INTERNAL_ERROR,
                                         "Failed to get world info: " + response.getError());
                }
            } else {
                throw new RpcException(RpcException.RpcErrorType.INTERNAL_ERROR,
                                     "Unexpected response type: " + result.getClass());
            }
        } catch (Exception e) {
            throw new RpcException(RpcException.RpcErrorType.INTERNAL_ERROR,
                                 "getWorldInfo failed: " + e.getMessage(), null, e);
        }
    }

    @Override
    public CompletableFuture<WorldInfo> getWorldInfoAsync(int worldId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return getWorldInfo(worldId);
            } catch (RpcException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public boolean enterWorld(long playerId, int worldId) throws RpcException {
        try {
            ActorRef worldActor = getWorldActor(worldId);
            Timeout timeout = Timeout.apply(3, TimeUnit.SECONDS);
            
            WorldMessages.EnterWorldCommand command = new WorldMessages.EnterWorldCommand(playerId, worldId);
            Future<Object> future = Patterns.ask(worldActor, command, timeout);
            
            Object result = Await.result(future, Duration.apply(3, TimeUnit.SECONDS));
            
            // EnterWorldCommand通常不会有返回值，成功即视为成功
            return true;
        } catch (Exception e) {
            throw new RpcException(RpcException.RpcErrorType.INTERNAL_ERROR,
                                 "enterWorld failed: " + e.getMessage(), null, e);
        }
    }

    @Override
    public boolean leaveWorld(long playerId, int worldId) throws RpcException {
        try {
            ActorRef worldActor = getWorldActor(worldId);
            Timeout timeout = Timeout.apply(3, TimeUnit.SECONDS);
            
            LeaveWorldMessage message = new LeaveWorldMessage(playerId);
            Future<Object> future = Patterns.ask(worldActor, message, timeout);
            
            Object result = Await.result(future, Duration.apply(3, TimeUnit.SECONDS));
            
            if (result instanceof CommandResult) {
                return ((CommandResult) result).isSuccess();
            } else {
                return true; // 成功离开
            }
        } catch (Exception e) {
            throw new RpcException(RpcException.RpcErrorType.INTERNAL_ERROR,
                                 "leaveWorld failed: " + e.getMessage(), null, e);
        }
    }

    @Override
    public boolean broadcastToWorld(int worldId, String message) throws RpcException {
        try {
            ActorRef worldActor = getWorldActor(worldId);
            Timeout timeout = Timeout.apply(3, TimeUnit.SECONDS);
            
            BroadcastMessage broadcastMsg = new BroadcastMessage(message);
            Future<Object> future = Patterns.ask(worldActor, broadcastMsg, timeout);
            
            Object result = Await.result(future, Duration.apply(3, TimeUnit.SECONDS));
            
            if (result instanceof CommandResult) {
                return ((CommandResult) result).isSuccess();
            } else {
                return true; // 广播成功
            }
        } catch (Exception e) {
            throw new RpcException(RpcException.RpcErrorType.INTERNAL_ERROR,
                                 "broadcastToWorld failed: " + e.getMessage(), null, e);
        }
    }

    @Override
    public int getOnlinePlayerCount(int worldId) throws RpcException {
        try {
            ActorRef worldActor = getWorldActor(worldId);
            Timeout timeout = Timeout.apply(2, TimeUnit.SECONDS);
            
            Future<Object> future = Patterns.ask(worldActor,
                new GetPlayerCountMessage(), timeout);
            
            Object result = Await.result(future, Duration.apply(2, TimeUnit.SECONDS));
            
            if (result instanceof PlayerCountResponse) {
                return ((PlayerCountResponse) result).getPlayerCount();
            } else {
                throw new RpcException(RpcException.RpcErrorType.INTERNAL_ERROR,
                                     "Unexpected response type: " + result.getClass());
            }
        } catch (Exception e) {
            throw new RpcException(RpcException.RpcErrorType.INTERNAL_ERROR,
                                 "getOnlinePlayerCount failed: " + e.getMessage(), null, e);
        }
    }

    @Override
    public Object invokeMethod(String methodName, Object... parameters) throws RpcException {
        switch (methodName) {
            case "getWorldInfo":
                if (parameters.length != 1 || !(parameters[0] instanceof Integer)) {
                    throw new RpcException(RpcException.RpcErrorType.INVALID_PARAMETERS,
                                         "getWorldInfo requires single Integer parameter");
                }
                return getWorldInfo((Integer) parameters[0]);
                
            case "enterWorld":
                if (parameters.length != 2 || 
                    !(parameters[0] instanceof Long) || 
                    !(parameters[1] instanceof Integer)) {
                    throw new RpcException(RpcException.RpcErrorType.INVALID_PARAMETERS,
                                         "enterWorld requires (Long, Integer) parameters");
                }
                return enterWorld((Long) parameters[0], (Integer) parameters[1]);
                
            case "leaveWorld":
                if (parameters.length != 2 || 
                    !(parameters[0] instanceof Long) || 
                    !(parameters[1] instanceof Integer)) {
                    throw new RpcException(RpcException.RpcErrorType.INVALID_PARAMETERS,
                                         "leaveWorld requires (Long, Integer) parameters");
                }
                return leaveWorld((Long) parameters[0], (Integer) parameters[1]);
                
            case "broadcastToWorld":
                if (parameters.length != 2 || 
                    !(parameters[0] instanceof Integer) || 
                    !(parameters[1] instanceof String)) {
                    throw new RpcException(RpcException.RpcErrorType.INVALID_PARAMETERS,
                                         "broadcastToWorld requires (Integer, String) parameters");
                }
                return broadcastToWorld((Integer) parameters[0], (String) parameters[1]);
                
            case "getOnlinePlayerCount":
                if (parameters.length != 1 || !(parameters[0] instanceof Integer)) {
                    throw new RpcException(RpcException.RpcErrorType.INVALID_PARAMETERS,
                                         "getOnlinePlayerCount requires single Integer parameter");
                }
                return getOnlinePlayerCount((Integer) parameters[0]);
                
            default:
                throw new RpcException(RpcException.RpcErrorType.METHOD_NOT_FOUND,
                                     "Method not found: " + methodName);
        }
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String getHealthStatus() {
        return "healthy";
    }

    /**
     * 根据世界ID获取对应的WorldActor引用
     */
    private ActorRef getWorldActor(int worldId) throws Exception {
        String serviceName = "World-" + worldId;
        CompletableFuture<ActorRef> future = OrionServices.lookupService(actorSystem, serviceName);
        ActorRef actorRef = future.get(3, TimeUnit.SECONDS);
        
        if (actorRef == null) {
            throw new RpcException(RpcException.RpcErrorType.SERVICE_NOT_FOUND,
                                 "World service not found: " + serviceName);
        }
        return actorRef;
    }

    // 内部消息类
    public static class GetWorldInfoMessage implements java.io.Serializable {}
    
    public static class WorldInfoResponse implements java.io.Serializable {
        private final WorldInfo worldInfo;
        private final String error;
        private final boolean success;
        
        public WorldInfoResponse(WorldInfo worldInfo) {
            this.worldInfo = worldInfo;
            this.error = null;
            this.success = true;
        }
        
        public WorldInfoResponse(String error) {
            this.worldInfo = null;
            this.error = error;
            this.success = false;
        }
        
        public WorldInfo getWorldInfo() { return worldInfo; }
        public String getError() { return error; }
        public boolean isSuccess() { return success; }
    }
    
    public static class LeaveWorldMessage implements java.io.Serializable {
        private final long playerId;
        
        public LeaveWorldMessage(long playerId) {
            this.playerId = playerId;
        }
        
        public long getPlayerId() { return playerId; }
    }
    
    public static class BroadcastMessage implements java.io.Serializable {
        private final String message;
        
        public BroadcastMessage(String message) {
            this.message = message;
        }
        
        public String getMessage() { return message; }
    }
    
    public static class GetPlayerCountMessage implements java.io.Serializable {}
    
    public static class PlayerCountResponse implements java.io.Serializable {
        private final int playerCount;
        
        public PlayerCountResponse(int playerCount) {
            this.playerCount = playerCount;
        }
        
        public int getPlayerCount() { return playerCount; }
    }
    
    public static class CommandResult implements java.io.Serializable {
        private final boolean success;
        private final String error;
        
        public CommandResult(boolean success) {
            this(success, null);
        }
        
        public CommandResult(boolean success, String error) {
            this.success = success;
            this.error = error;
        }
        
        public boolean isSuccess() { return success; }
        public String getError() { return error; }
    }
}