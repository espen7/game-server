package game.engine.world.bootstrap;

import game.engine.core.ProcessType;
import game.engine.core.bootstrap.AbstractBootstrap;
import game.engine.core.bootstrap.BootstrapContext;
import game.engine.world.actor.WorldServiceActor;
import org.apache.pekko.actor.ActorSystem;

/**
 * World Actor Bootstrap。
 * 
 * <p>负责初始化 World 服务的 WorldServiceActor。
 * 
 * <h2>使用示例</h2>
 * <pre>
 * WorldActorBootstrap bootstrap = new WorldActorBootstrap();
 * bootstrap.init(context);
 * </pre>
 * 
 * @since 1.0
 */
public class WorldActorBootstrap extends AbstractBootstrap {
    
    public WorldActorBootstrap() {
        super("WorldActorBootstrap");
    }
    
    @Override
    public int getPriority() {
        return 30; // 核心组件优先级
    }
    
    @Override
    protected void doInit(BootstrapContext context) throws Exception {
        ActorSystem system = context.getActorSystem();
        int instanceId = context.getInstanceId();
        
        // worldId = instanceId + 1 (保持兼容性)
        int worldId = instanceId + 1;
        
        // Create World Actor
        system.actorOf(WorldServiceActor.props(worldId), "world-" + worldId);
        
        logger.info("World Actor created for World ID: {} on port: {}", 
                worldId, ProcessType.WORLD.getPort(instanceId));
    }
    
    @Override
    protected void doShutdown() throws Exception {
        // Actor 会随 ActorSystem 一起关闭，无需显式清理
        logger.info("World Actor will be stopped with ActorSystem");
    }
}
