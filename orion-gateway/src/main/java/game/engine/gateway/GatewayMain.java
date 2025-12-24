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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GatewayMain {
    private static final Logger logger = LoggerFactory.getLogger(GatewayMain.class);

    public static void main(String[] args) {
        // 1. Create ActorSystem
        ActorSystem actorSystem = ActorSystem.create("GatewaySystem");
        logger.info("ActorSystem created: {}", actorSystem.name());

        // 2. Configure Netty
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

        // 3. Start Server
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
