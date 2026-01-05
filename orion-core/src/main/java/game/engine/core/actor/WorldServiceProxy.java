package game.engine.core.actor;

import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import org.apache.pekko.actor.Terminated;
import org.apache.pekko.event.Logging;
import org.apache.pekko.event.LoggingAdapter;
import game.engine.core.OrionServices;

import java.util.HashMap;
import java.util.Map;

/**
 * World 服务代理
 * 提供高性能的 Gateway 到 World 通信，自动处理死信和重连
 * 
 * 特性：
 * 1. 缓存 World ActorRef，避免重复查找
 * 2. 监控 World Actor 生命周期，自动处理死信
 * 3. World 重启时自动重新查找
 * 4. 支持多个 World 实例
 */
public class WorldServiceProxy extends AbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    
    // 缓存 worldId -> ActorRef 的映射
    private final Map<Integer, ActorRef> worldActorCache = new HashMap<>();
    
    // 标记正在查找的 worldId，避免重复查找
    private final Map<Integer, Boolean> lookupInProgress = new HashMap<>();

    public static Props props() {
        return Props.create(WorldServiceProxy.class, WorldServiceProxy::new);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(ForwardToWorld.class, this::handleForward)
                .match(Terminated.class, this::handleTerminated)
                .match(WorldActorFound.class, this::handleWorldActorFound)
                .build();
    }

    private void handleForward(ForwardToWorld forward) {
        ActorRef worldRef = worldActorCache.get(forward.worldId);
        
        if (worldRef != null) {
            // 缓存命中，直接转发（高性能路径）
            worldRef.tell(forward.message, forward.sender);
        } else {
            // 缓存未命中，需要查找
            if (!lookupInProgress.getOrDefault(forward.worldId, false)) {
                // 开始查找
                lookupInProgress.put(forward.worldId, true);
                lookupWorldActor(forward.worldId);
            }
            
            // 查找期间，使用 DistributedPubSub 作为后备方案
            String worldPath = "/user/world-" + forward.worldId;
            OrionServices.sendToService(
                getContext().getSystem(),
                worldPath,
                forward.message,
                forward.sender
            );
            
            log.debug("World {} not in cache, using PubSub fallback", forward.worldId);
        }
    }

    private void lookupWorldActor(int worldId) {
        String worldActorName = "world-" + worldId;
        
        OrionServices.lookupService(getContext().getSystem(), worldActorName)
            .thenAccept(actorRef -> {
                if (actorRef != null) {
                    // 查找成功，通知自己
                    getSelf().tell(new WorldActorFound(worldId, actorRef), getSelf());
                } else {
                    log.warning("World {} not found", worldId);
                    lookupInProgress.put(worldId, false);
                }
            });
    }

    private void handleWorldActorFound(WorldActorFound found) {
        log.info("World {} found and cached: {}", found.worldId, found.actorRef);
        
        // 缓存 ActorRef
        worldActorCache.put(found.worldId, found.actorRef);
        
        // 监控 World Actor，检测死信
        getContext().watch(found.actorRef);
        
        // 标记查找完成
        lookupInProgress.put(found.worldId, false);
    }

    private void handleTerminated(Terminated terminated) {
        // World Actor 终止，从缓存中移除
        ActorRef terminatedActor = terminated.getActor();
        
        worldActorCache.entrySet().removeIf(entry -> {
            if (entry.getValue().equals(terminatedActor)) {
                log.warning("World {} terminated, removed from cache", entry.getKey());
                return true;
            }
            return false;
        });
    }

    /**
     * 转发消息到 World 的包装类
     */
    public static class ForwardToWorld {
        public final int worldId;
        public final Object message;
        public final ActorRef sender;

        public ForwardToWorld(int worldId, Object message, ActorRef sender) {
            this.worldId = worldId;
            this.message = message;
            this.sender = sender;
        }
    }

    /**
     * World Actor 查找成功的通知
     */
    private static class WorldActorFound {
        public final int worldId;
        public final ActorRef actorRef;

        public WorldActorFound(int worldId, ActorRef actorRef) {
            this.worldId = worldId;
            this.actorRef = actorRef;
        }
    }
}
