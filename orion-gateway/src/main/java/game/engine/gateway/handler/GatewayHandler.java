package game.engine.gateway.handler;

import game.engine.gateway.actor.ChannelActor;
import game.engine.gateway.codec.Packet;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.AttributeKey;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GatewayHandler extends SimpleChannelInboundHandler<Packet> {
    private static final Logger logger = LoggerFactory.getLogger(GatewayHandler.class);
    public static final AttributeKey<ActorRef> CHANNEL_ACTOR_KEY = AttributeKey.valueOf("channelActor");
    
    private final ActorSystem actorSystem;
    private final String gatewayId;

    public GatewayHandler(ActorSystem actorSystem, String gatewayId) {
        this.actorSystem = actorSystem;
        this.gatewayId = gatewayId;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        ActorRef channelActor = actorSystem.actorOf(ChannelActor.props(ctx.channel(), gatewayId));
        ctx.channel().attr(CHANNEL_ACTOR_KEY).set(channelActor);
        logger.info("Channel active, bound ChannelActor: {}", channelActor);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        ActorRef channelActor = ctx.channel().attr(CHANNEL_ACTOR_KEY).get();
        if (channelActor != null) {
            actorSystem.stop(channelActor);
            logger.info("Channel inactive, stopped ChannelActor: {}", channelActor);
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) throws Exception {
        ActorRef channelActor = ctx.channel().attr(CHANNEL_ACTOR_KEY).get();
        if (channelActor != null) {
            channelActor.tell(packet, ActorRef.noSender());
        } else {
            logger.warn("Received packet but no ChannelActor bound to channel: {}", ctx.channel().id());
        }
    }



    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("GatewayHandler exception", cause);
        ctx.close();
    }
}
