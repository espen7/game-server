package game.engine.gateway;

import game.engine.gateway.codec.Packet;
import game.engine.gateway.codec.PacketDecoder;
import game.engine.gateway.codec.PacketEncoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class GatewayIntegrationTest {

    private static Thread serverThread;

    @BeforeAll
    public static void setup() throws InterruptedException {
        serverThread = new Thread(() -> {
            GatewayMain.main(new String[]{});
        });
        serverThread.start();
        // Wait for server to start
        TimeUnit.SECONDS.sleep(2);
    }

    @AfterAll
    public static void teardown() {
        if (serverThread != null) {
            serverThread.interrupt();
        }
    }

    @Test
    public void testClientConnection() throws InterruptedException {
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
             .channel(NioSocketChannel.class)
             .handler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 public void initChannel(SocketChannel ch) throws Exception {
                     ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(1024 * 1024, 0, 4, 0, 4));
                     ch.pipeline().addLast(new LengthFieldPrepender(4));
                     ch.pipeline().addLast(new PacketDecoder());
                     ch.pipeline().addLast(new PacketEncoder());
                 }
             });

            ChannelFuture f = b.connect("localhost", 8080).sync();
            assertTrue(f.channel().isActive());

            // Send a packet
            Packet packet = new Packet(1, "test".getBytes());
            f.channel().writeAndFlush(packet).sync();

            // Wait a bit to see logs (manual verification or we could use a mock actor to verify)
            TimeUnit.SECONDS.sleep(1);

            f.channel().close().sync();
        } finally {
            group.shutdownGracefully();
        }
    }
}
