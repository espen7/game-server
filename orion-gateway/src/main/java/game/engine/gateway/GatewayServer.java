package game.engine.gateway;

import game.engine.core.ProcessType;
import game.engine.core.bootstrap.BootstrapManager;
import game.engine.core.channel.ChannelBootstrap;
import game.engine.core.server.AbstractServer;
import game.engine.gateway.bootstrap.ActorProxyBootstrap;
import game.engine.gateway.bootstrap.NettyBootstrap;
import org.apache.pekko.actor.ActorSystem;

/**
 * Gateway 服务器。
 * 
 * <p>使用 Bootstrap 架构组装启动组件：
 * <ul>
 *   <li>ActorProxyBootstrap - Actor 代理（优先级 40）</li>
 *   <li>ChannelBootstrap - 批处理通道系统（优先级 50）</li>
 *   <li>NettyBootstrap - TCP/WebSocket 服务器（优先级 90）</li>
 * </ul>
 */
public class GatewayServer extends AbstractServer {

    public static void main(String[] args) {
        new GatewayServer().boot(args);
    }

    @Override
    protected ProcessType getProcessType() {
        return ProcessType.GATEWAY;
    }

    @Override
    protected boolean shouldJoinCluster() {
        // Gateway 0 is the seed node, so it doesn't need to "join" another seed.
        // Others (instanceId > 0) must join.
        return instanceId > 0;
    }

    @Override
    protected void registerBootstraps(BootstrapManager manager) {
        // 1. 注册 ActorProxyBootstrap（优先级 40）
        manager.register(new ActorProxyBootstrap());
        
        // 2. 注册 ChannelBootstrap（优先级 50）
        manager.register(new ChannelBootstrap());
        
        // 3. 注册 NettyBootstrap（优先级 90）
        int nettyPort = 8080 + instanceId;
        String gatewayId = "gateway-" + instanceId;
        
        NettyBootstrap nettyBootstrap = NettyBootstrap.builder()
                .port(nettyPort)
                .gatewayId(gatewayId)
                .build();
        
        manager.register(nettyBootstrap);
        
        logger.info("Registered Gateway bootstraps: ActorProxyBootstrap, ChannelBootstrap, NettyBootstrap");
    }
}
