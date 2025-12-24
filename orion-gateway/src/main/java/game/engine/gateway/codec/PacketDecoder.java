package game.engine.gateway.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

public class PacketDecoder extends ByteToMessageDecoder {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // LengthFieldBasedFrameDecoder should handle the length prefix before this
        if (in.readableBytes() < 4) {
            return;
        }

        int msgId = in.readInt();
        byte[] body = new byte[in.readableBytes()];
        in.readBytes(body);

        out.add(new Packet(msgId, body));
    }
}
