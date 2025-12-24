package game.engine.gateway.net;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class ServerBootstrapFactory {
    private static final Logger logger = LoggerFactory.getLogger(ServerBootstrapFactory.class);

    public static ServerBootstrap createBootstrap(NettyServerConfig config) {
        ServerBootstrap bootstrap = new ServerBootstrap();
        EventLoopGroup bossGroup;
        EventLoopGroup workerGroup;

        if (config.isUseEpoll() && Epoll.isAvailable()) {
            logger.info("Using Epoll for Gateway Server");
            bossGroup = new EpollEventLoopGroup(config.getBossThreads(), new NamedThreadFactory("gateway-epoll-boss"));
            workerGroup = new EpollEventLoopGroup(config.getWorkerThreads(), new NamedThreadFactory("gateway-epoll-worker"));
            bootstrap.group(bossGroup, workerGroup)
                    .channel(EpollServerSocketChannel.class);
        } else {
            logger.info("Using NIO for Gateway Server");
            bossGroup = new NioEventLoopGroup(config.getBossThreads(), new NamedThreadFactory("gateway-nio-boss"));
            workerGroup = new NioEventLoopGroup(config.getWorkerThreads(), new NamedThreadFactory("gateway-nio-worker"));
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class);
        }

        return bootstrap;
    }

    private static class NamedThreadFactory implements ThreadFactory {
        private final String namePrefix;
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        public NamedThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, namePrefix + "-" + threadNumber.getAndIncrement());
        }
    }
}
