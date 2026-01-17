package game.engine.gateway.bootstrap;

import game.engine.core.bootstrap.AbstractBootstrap;
import game.engine.core.bootstrap.BootstrapContext;
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

/**
 * Netty 服务器 Bootstrap。
 * 
 * <p>封装 Netty TCP/WebSocket 服务器的启动和关闭逻辑。
 * 
 * <h2>使用示例</h2>
 * <pre>
 * NettyBootstrap nettyBootstrap = NettyBootstrap.builder()
 *     .port(8080)
 *     .gatewayId("gateway-0")
 *     .build();
 * 
 * nettyBootstrap.init(context);
 * </pre>
 * 
 * @since 1.0
 */
public class NettyBootstrap extends AbstractBootstrap {
    
    private final int port;
    private final String gatewayId;
    private final NettyServerConfig config;
    
    private ServerBootstrap serverBootstrap;
    private ChannelFuture channelFuture;
    
    private NettyBootstrap(Builder builder) {
        super("NettyBootstrap");
        this.port = builder.port;
        this.gatewayId = builder.gatewayId;
        this.config = new NettyServerConfig(port);
        
        // 应用自定义配置
        if (builder.bossThreads > 0) {
            config.setBossThreads(builder.bossThreads);
        }
        if (builder.workerThreads > 0) {
            config.setWorkerThreads(builder.workerThreads);
        }
        config.setUseEpoll(builder.useEpoll);
    }
    
    @Override
    public int getPriority() {
        return 90; // 网络层，较晚启动
    }
    
    @Override
    protected void doInit(BootstrapContext context) throws Exception {
        ActorSystem actorSystem = context.getActorSystem();
        
        // 创建 Netty ServerBootstrap
        this.serverBootstrap = ServerBootstrapFactory.createBootstrap(config);
        
        // 配置 Pipeline
        serverBootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast(new io.netty.handler.codec.LengthFieldBasedFrameDecoder(
                        1024 * 1024, 0, 4, 0, 4));
                ch.pipeline().addLast(new io.netty.handler.codec.LengthFieldPrepender(4));
                ch.pipeline().addLast(new PacketDecoder());
                ch.pipeline().addLast(new PacketEncoder());
                ch.pipeline().addLast(new GatewayHandler(actorSystem, gatewayId));
            }
        })
        .option(ChannelOption.SO_BACKLOG, 128)
        .childOption(ChannelOption.SO_KEEPALIVE, true)
        .childOption(ChannelOption.TCP_NODELAY, true);
        
        // 绑定端口
        this.channelFuture = serverBootstrap.bind(config.getPort()).sync();
        logger.info("Netty Server started on port: {}", config.getPort());
    }
    
    @Override
    protected void doShutdown() throws Exception {
        // 关闭 Channel
        if (channelFuture != null) {
            channelFuture.channel().close().sync();
        }
        
        // 关闭线程池
        if (serverBootstrap != null) {
            if (serverBootstrap.config().group() != null) {
                serverBootstrap.config().group().shutdownGracefully().sync();
            }
            if (serverBootstrap.config().childGroup() != null) {
                serverBootstrap.config().childGroup().shutdownGracefully().sync();
            }
        }
        
        logger.info("Netty Server stopped");
    }
    
    /**
     * 创建 Builder
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder 模式
     */
    public static class Builder {
        private int port = 8080;
        private String gatewayId = "gateway-0";
        private int bossThreads = -1;
        private int workerThreads = -1;
        private boolean useEpoll = true;
        
        public Builder port(int port) {
            this.port = port;
            return this;
        }
        
        public Builder gatewayId(String gatewayId) {
            this.gatewayId = gatewayId;
            return this;
        }
        
        public Builder bossThreads(int bossThreads) {
            this.bossThreads = bossThreads;
            return this;
        }
        
        public Builder workerThreads(int workerThreads) {
            this.workerThreads = workerThreads;
            return this;
        }
        
        public Builder useEpoll(boolean useEpoll) {
            this.useEpoll = useEpoll;
            return this;
        }
        
        public NettyBootstrap build() {
            return new NettyBootstrap(this);
        }
    }
}
