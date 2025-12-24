package game.engine.gateway.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class PacketEncoder extends MessageToByteEncoder<Packet> {
    @Override
    protected void encode(ChannelHandlerContext ctx, Packet msg, ByteBuf out) throws Exception {
        // Length will be prepended by LengthFieldPrepender if used, 
        // or we can write it here if we don't use LengthFieldPrepender.
        // Let's assume we use LengthFieldPrepender in the pipeline.
        
        out.writeInt(msg.getMsgId());
        if (msg.getBody() != null) {
            out.writeBytes(msg.getBody());
        }
    }
}
