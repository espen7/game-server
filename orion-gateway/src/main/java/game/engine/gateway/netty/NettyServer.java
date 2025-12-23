package game.engine.gateway.netty;

import org.apache.pekko.actor.ActorSystem;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NettyServer {
    private static final Logger logger = LoggerFactory.getLogger(NettyServer.class);
    private final int port;
    private final boolean isWebSocket;
    private final ActorSystem system;

    public NettyServer(int port, boolean isWebSocket, ActorSystem system) {
        this.port = port;
        this.isWebSocket = isWebSocket;
        this.system = system;
    }

    public void start() {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline p = ch.pipeline();
                            if (isWebSocket) {
                                p.addLast(new HttpServerCodec());
                                p.addLast(new HttpObjectAggregator(65536));
                                p.addLast("ws-handler", new WebSocketServerProtocolHandler("/ws"));
                            } else {
                                p.addLast(new StringDecoder());
                                p.addLast(new StringEncoder());
                            }
                            p.addLast(new OrionServerHandler(system));
                        }
                    });

            ChannelFuture f = b.bind(port).sync();
            logger.info("Netty Server started on port {} (WebSocket: {})", port, isWebSocket);
            f.channel().closeFuture().sync(); // 这会阻塞，通常在单独的线程中运�?
        } catch (InterruptedException e) {
            logger.error("Netty Server interrupted", e);
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
