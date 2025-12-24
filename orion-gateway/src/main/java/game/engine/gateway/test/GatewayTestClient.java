package game.engine.gateway.test;

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

public class GatewayTestClient {
    public static void main(String[] args) throws InterruptedException {
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

            // Send Heartbeat (ID 1)
            sendPacket(f, 1, "Heartbeat".getBytes());
            
            // Send Home Message (ID 1001)
            sendPacket(f, 1001, "Home Request".getBytes());
            
            // Send World Message (ID 2001)
            sendPacket(f, 2001, "World Request".getBytes());

            f.channel().closeFuture().sync();
        } finally {
            group.shutdownGracefully();
        }
    }

    private static void sendPacket(ChannelFuture f, int msgId, byte[] body) {
        Packet packet = new Packet(msgId, body);
        f.channel().writeAndFlush(packet);
        System.out.println("Sent message ID: " + msgId);
    }
}
