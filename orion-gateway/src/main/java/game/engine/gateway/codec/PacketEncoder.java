package game.engine.gateway.codec;

import game.engine.core.message.Letter;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class PacketEncoder extends MessageToByteEncoder<Letter> {
    @Override
    protected void encode(ChannelHandlerContext ctx, Letter msg, ByteBuf out) throws Exception {
        // Length will be prepended by LengthFieldPrepender if used, 
        // or we can write it here if we don't use LengthFieldPrepender.
        // Let's assume we use LengthFieldPrepender in the pipeline.
        
        out.writeInt(msg.msgId());
        if (msg.payload() != null) {
            out.writeBytes(msg.payload());
        }
    }
}
