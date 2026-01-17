package game.engine.gateway.bootstrap;

import game.engine.core.OrionServices;
import game.engine.core.actor.PlayerShardingConfig;
import game.engine.core.actor.PortalServiceProxy;
import game.engine.core.actor.WorldServiceProxy;
import game.engine.core.bootstrap.AbstractBootstrap;
import game.engine.core.bootstrap.BootstrapContext;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.cluster.sharding.ClusterSharding;

import java.util.Optional;

/**
 * Actor 代理 Bootstrap。
 * 
 * <p>负责初始化 Gateway 所需的 Actor 代理：
 * <ul>
 *   <li>PlayerShardingProxy - Player 分片代理</li>
 *   <li>PortalServiceProxy - Portal 服务代理（Group Router）</li>
 *   <li>WorldServiceProxy - World 服务代理（Global Singleton）</li>
 * </ul>
 * 
 * <h2>使用示例</h2>
 * <pre>
 * ActorProxyBootstrap proxyBootstrap = new ActorProxyBootstrap();
 * proxyBootstrap.init(context);
 * </pre>
 * 
 * @since 1.0
 */
public class ActorProxyBootstrap extends AbstractBootstrap {
    
    public ActorProxyBootstrap() {
        super("ActorProxyBootstrap");
    }
    
    @Override
    public int getPriority() {
        return 40; // 在核心组件之后，网络层之前
    }
    
    @Override
    protected void doInit(BootstrapContext context) throws Exception {
        ActorSystem system = context.getActorSystem();
        
        // 1. Initial Player Sharding Proxy
        ClusterSharding.get(system).startProxy(
                PlayerShardingConfig.TYPE_NAME,
                Optional.of("player"),
                PlayerShardingConfig.MESSAGE_EXTRACTOR);
        logger.info("PlayerShardingProxy started");
        
        // 2. Create Portal Service Proxy (Group Router)
        system.actorOf(PortalServiceProxy.props(), OrionServices.PORTAL_SERVICE_PROXY_NAME);
        logger.info("PortalServiceProxy created with Group Router");
        
        // 3. Create World Service Proxy (Global Singleton)
        system.actorOf(WorldServiceProxy.props(), OrionServices.WORLD_SERVICE_PROXY_NAME);
        logger.info("WorldServiceProxy created");
    }
    
    @Override
    protected void doShutdown() throws Exception {
        // Actor 代理会随 ActorSystem 一起关闭，无需显式清理
        logger.info("Actor proxies will be stopped with ActorSystem");
    }
}
