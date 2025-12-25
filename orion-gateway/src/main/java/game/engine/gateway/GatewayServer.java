package game.engine.gateway;

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
import game.engine.player.actor.PlayerActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GatewayServer {

    private static final Logger logger = LoggerFactory.getLogger(GatewayServer.class);

    public static void main(String[] args) {
        // 1. 启动 Pekko System (Gateway 节点)
        ActorSystem system = OrionEngine.create()
                .withRole("gateway")
                .withPort(2552)
                .withSeedNodes("127.0.0.1:2551") // 加入 GameServer
                .start();
        logger.info("ActorSystem created: {}", system.name());

        // 2. 初始 Sharding Proxy
        org.apache.pekko.cluster.sharding.ClusterSharding.get(system).startProxy(
                PlayerActor.TYPE_NAME,
                java.util.Optional.of("player"),
                PlayerActor.messageExtractor
        );

        // 3. 启动 Netty Server (WebSocket 端口 8080)
        bootstrap(system);
    }

    private static void bootstrap(ActorSystem actorSystem) {
        // 1. Configure Netty
        String gatewayId = "gateway-1"; // Should come from config
        NettyServerConfig config = new NettyServerConfig(8080);
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
