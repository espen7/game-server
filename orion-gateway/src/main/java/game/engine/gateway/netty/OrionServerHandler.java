package game.engine.gateway.netty;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import game.engine.gateway.actor.ChannelActor;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

public class OrionServerHandler extends SimpleChannelInboundHandler<Object> {
    private final ActorSystem system;
    private ActorRef channelActor;

    public OrionServerHandler(ActorSystem system) {
        this.system = system;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        // 为此连接创建 ChannelActor
        this.channelActor = system.actorOf(ChannelActor.props(ctx.channel()));
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        if (channelActor != null) {
            system.stop(channelActor);
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        String content = null;
        if (msg instanceof TextWebSocketFrame) {
            content = ((TextWebSocketFrame) msg).text();
        } else if (msg instanceof String) {
            content = (String) msg;
        }

        if (content != null && channelActor != null) {
            channelActor.tell(content, ActorRef.noSender());
        }
    }
}
