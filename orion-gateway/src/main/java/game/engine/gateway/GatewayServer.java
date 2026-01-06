package game.engine.gateway;

import game.engine.core.ProcessType;
import game.engine.core.actor.PortalServiceProxy;
import game.engine.core.actor.PlayerShardingConfig;
import game.engine.core.actor.WorldServiceProxy;
import game.engine.core.OrionServices;
import game.engine.gateway.codec.PacketDecoder;
import game.engine.gateway.codec.PacketEncoder;
import game.engine.gateway.handler.GatewayHandler;
import game.engine.gateway.net.NettyServerConfig;
import game.engine.gateway.net.ServerBootstrapFactory;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.SocketChannel;
import org.apache.pekko.actor.ActorSystem;
import game.engine.core.OrionEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GatewayServer {

    private static final Logger logger = LoggerFactory.getLogger(GatewayServer.class);

    public static void main(String[] args) {
        // 解析网关实例ID（默认为0）
        int instanceId = 0;
        if (args.length > 0) {
            try {
                instanceId = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid instance ID, defaulting to 0");
            }
        }

        // 1. 启动 Pekko System (Gateway 节点)
        OrionEngine engine = OrionEngine.create()
                .withProcessType(ProcessType.GATEWAY)
                .withPort(ProcessType.GATEWAY.getPort(instanceId));
        
        // 如果不是实例0，则需要连接到实例0作为 seed node
        if (instanceId > 0) {
            engine.withDefaultSeedNode();
        }
        
        ActorSystem system = engine.start();
        logger.info("ActorSystem created: {}, instance: {}, port: {}", 
                system.name(), instanceId, ProcessType.GATEWAY.getPort(instanceId));

        // 2. 初始 Sharding Proxy
        org.apache.pekko.cluster.sharding.ClusterSharding.get(system).startProxy(
                PlayerShardingConfig.TYPE_NAME,
                java.util.Optional.of("player"),
                PlayerShardingConfig.MESSAGE_EXTRACTOR
        );

        // 创建 Portal 服务代理（Group Router，负载均衡）
        system.actorOf(PortalServiceProxy.props(), OrionServices.PORTAL_SERVICE_PROXY_NAME);
        logger.info("PortalServiceProxy created with Group Router");

        // 创建 World 服务代理（全局单例，处理所有到 World 的通信）
        system.actorOf(WorldServiceProxy.props(), OrionServices.WORLD_SERVICE_PROXY_NAME);
        logger.info("WorldServiceProxy created");

        // 3. 启动 Netty Server (WebSocket 端口，根据实例ID分配）
        int nettyPort = 8080 + instanceId;
        bootstrap(system, instanceId, nettyPort);
    }

    private static void bootstrap(ActorSystem actorSystem, int instanceId, int nettyPort) {
        // 1. Configure Netty
        String gatewayId = "gateway-" + instanceId;
        NettyServerConfig config = new NettyServerConfig(nettyPort);
        ServerBootstrap bootstrap = ServerBootstrapFactory.createBootstrap(config);

        bootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast(new io.netty.handler.codec.LengthFieldBasedFrameDecoder(1024 * 1024, 0, 4, 0, 4));
                        ch.pipeline().addLast(new io.netty.handler.codec.LengthFieldPrepender(4));
                        ch.pipeline().addLast(new PacketDecoder());
                        ch.pipeline().addLast(new PacketEncoder());
                        // Pass ActorSystem and gatewayId to GatewayHandler
                        ch.pipeline().addLast(new GatewayHandler(actorSystem, gatewayId));
                    }
                })
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true);

        // 2. Start Server
        try {
            ChannelFuture channelFuture = bootstrap.bind(config.getPort()).sync();
            logger.info("Gateway Server started on port {}", config.getPort());

            // Shutdown Hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Stopping Gateway Server...");
                channelFuture.channel().close();
                bootstrap.config().group().shutdownGracefully();
                bootstrap.config().childGroup().shutdownGracefully();
                actorSystem.terminate();
                logger.info("Gateway Server stopped");
            }));

            channelFuture.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            logger.error("Gateway Server start failed", e);
            Thread.currentThread().interrupt();
        } finally {
            // If we reach here, we should probably shut down if not already handled by hook
            if (!actorSystem.whenTerminated().isCompleted()) {
                actorSystem.terminate();
            }
        }
    }
}
