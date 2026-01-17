package game.engine.gateway;

import game.engine.core.OrionServices;
import game.engine.core.ProcessType;
import game.engine.core.actor.PlayerShardingConfig;
import game.engine.core.actor.PortalServiceProxy;
import game.engine.core.actor.WorldServiceProxy;
import game.engine.core.server.AbstractServer;
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
import org.apache.pekko.cluster.sharding.ClusterSharding;

import java.util.Optional;

public class GatewayServer extends AbstractServer {

    private ChannelFuture channelFuture;
    private ServerBootstrap bootstrap;

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
    protected void onStart(ActorSystem system, int instanceId) {
        // 1. Initial Sharding Proxy
        ClusterSharding.get(system).startProxy(
                PlayerShardingConfig.TYPE_NAME,
                Optional.of("player"),
                PlayerShardingConfig.MESSAGE_EXTRACTOR);

        // 2. Create Portal Service Proxy (Group Router)
        system.actorOf(PortalServiceProxy.props(), OrionServices.PORTAL_SERVICE_PROXY_NAME);
        logger.info("PortalServiceProxy created with Group Router");

        // 3. Create World Service Proxy (Global Singleton)
        system.actorOf(WorldServiceProxy.props(), OrionServices.WORLD_SERVICE_PROXY_NAME);
        logger.info("WorldServiceProxy created");

        // 4. Start Netty Server
        int nettyPort = 8080 + instanceId;
        startNetty(system, instanceId, nettyPort);
    }

    private void startNetty(ActorSystem actorSystem, int instanceId, int nettyPort) {
        String gatewayId = "gateway-" + instanceId;
        NettyServerConfig config = new NettyServerConfig(nettyPort);
        this.bootstrap = ServerBootstrapFactory.createBootstrap(config);

        bootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast(new io.netty.handler.codec.LengthFieldBasedFrameDecoder(1024 * 1024, 0, 4, 0, 4));
                ch.pipeline().addLast(new io.netty.handler.codec.LengthFieldPrepender(4));
                ch.pipeline().addLast(new PacketDecoder());
                ch.pipeline().addLast(new PacketEncoder());
                ch.pipeline().addLast(new GatewayHandler(actorSystem, gatewayId));
            }
        })
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true);

        try {
            this.channelFuture = bootstrap.bind(config.getPort()).sync();
            logger.info("Gateway Server started on port {}", config.getPort());

            // Note: Shutdown hook is now handled by AbstractServer calling onStop()
            this.channelFuture.channel().closeFuture(); // Don't sync here, let the main thread exit?
            // Actually, AbstractServer.boot() finishes after onStart().
            // If we don't block, the main thread ends.
            // But ActorSystem keeps the JVM alive usually.
            // However, Netty also has threads.
            // The original code did: channelFuture.channel().closeFuture().sync();
            // This blocks the main thread.
            // If we return from onStart, AbstractServer.boot finishes.
            // Does ActorSystem keep JVM alive? Yes, non-daemon threads.
            // So we don't strictly need to block here.

        } catch (InterruptedException e) {
            logger.error("Gateway Server start failed", e);
            Thread.currentThread().interrupt();
        }
    }

    @Override
    protected void onStop() {
        if (channelFuture != null) {
            channelFuture.channel().close();
        }
        if (bootstrap != null) {
            if (bootstrap.config().group() != null)
                bootstrap.config().group().shutdownGracefully();
            if (bootstrap.config().childGroup() != null)
                bootstrap.config().childGroup().shutdownGracefully();
        }
    }
}
