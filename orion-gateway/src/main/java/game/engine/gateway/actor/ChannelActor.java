package game.engine.gateway.actor;

import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.Props;
import org.apache.pekko.cluster.sharding.ClusterSharding;
import org.apache.pekko.event.Logging;
import org.apache.pekko.event.LoggingAdapter;
import game.engine.core.OrionServices;
import game.engine.player.actor.PlayerActor;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

public class ChannelActor extends AbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    private final Channel channel;
    private final String gatewayId;
    private String playerId; // Bound after login

    public ChannelActor(Channel channel, String gatewayId) {
        this.channel = channel;
        this.gatewayId = gatewayId;
    }

    public static Props props(Channel channel, String gatewayId) {
        return Props.create(ChannelActor.class, () -> new ChannelActor(channel, gatewayId));
    }

    @Override
    public void preStart() {
        log.info("ChannelActor started for channel: {}", channel.id());
    }

    @Override
    public void postStop() {
        log.info("ChannelActor stopped for channel: {}", channel.id());
        if (channel.isOpen()) {
            channel.close();
        }
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(game.engine.gateway.codec.Packet.class, this::handlePacket)
                .match(game.engine.core.message.Envelope.class, this::handleEnvelope)
                .match(String.class, this::handleInboundMessage) // Keep for backward compatibility if needed
                .match(PlayerActor.PlayerMessage.class, this::handleOutboundMessage)
                .build();
    }

    private void handleEnvelope(game.engine.core.message.Envelope envelope) {
        log.info("ChannelActor received envelope: {}", envelope.getLetter().getMsgId());
        // TODO: Handle response from other services
    }

    private void handlePacket(game.engine.gateway.codec.Packet packet) {
        int msgId = packet.getMsgId();
        game.engine.gateway.handler.MessageRouter.Destination destination = game.engine.gateway.handler.MessageRouter.route(msgId);
        
        log.info("ChannelActor routing packet ID: {}, Destination: {}", msgId, destination);

        game.engine.core.message.Letter letter = new game.engine.core.message.Letter(msgId, packet.getBody());
        game.engine.core.message.Envelope envelope = new game.engine.core.message.Envelope(letter, playerId, gatewayId);

        switch (destination) {
            case GATEWAY:
                handleGatewayPacket(envelope);
                break;
            case HOME:
                forwardToHome(envelope);
                break;
            case WORLD:
                forwardToWorld(envelope);
                break;
            default:
                log.warning("Unknown message destination for ID: {}", msgId);
                break;
        }
    }

    private void handleGatewayPacket(game.engine.core.message.Envelope envelope) {
        // TODO: Implement Gateway internal message handling
        log.info("Handling Gateway envelope: {}", envelope.getLetter().getMsgId());
    }

    private void forwardToHome(game.engine.core.message.Envelope envelope) {
        // TODO: Forward to Home Server
        log.info("Forwarding envelope to Home: {}", envelope.getLetter().getMsgId());
    }

    private void forwardToWorld(game.engine.core.message.Envelope envelope) {
        // TODO: Forward to World Server
        log.info("Forwarding envelope to World: {}", envelope.getLetter().getMsgId());
    }

    private void handleInboundMessage(String msg) {
        log.info("Received from client: {}", msg);

        // 简单协�? "LOGIN|playerId" �?"CHAT|content"
        if (msg.startsWith("LOGIN|")) {
            this.playerId = msg.split("\\|")[1];
            log.info("Player logged in: {}", playerId);
            
            // 发送欢迎消息给客户�?
            sendToClient("Welcome " + playerId);
            
            // 通过 Sharding 通知 PlayerActor
            ClusterSharding.get(getContext().getSystem())
                    .shardRegion(PlayerActor.TYPE_NAME)
                    .tell(new PlayerActor.PlayerMessage(playerId, "Login from Gateway"), getSelf());
        } else if (msg.startsWith("MAP|")) {
             // Protocol: MAP|worldId|content
             String[] parts = msg.split("\\|", 3);
             if (parts.length < 3) {
                 sendToClient("Invalid MAP command format. Use: MAP|worldId|content");
                 return;
             }
             String targetWorldId = parts[1];
             String content = parts[2];
             
             // 路由�?WorldService-{id}
             OrionServices.sendToService(getContext().getSystem(), "WorldService-" + targetWorldId, content, getSelf());
        } else if (playerId != null) {
            // 路由�?PlayerActor
            ClusterSharding.get(getContext().getSystem())
                    .shardRegion(PlayerActor.TYPE_NAME)
                    .tell(new PlayerActor.PlayerMessage(playerId, msg), getSelf());
        } else {
            sendToClient("Please LOGIN first.");
        }
    }

    private void handleOutboundMessage(PlayerActor.PlayerMessage msg) {
        sendToClient("Server: " + msg.content);
    }

    private void sendToClient(String message) {
        if (channel.isOpen()) {
            // 根据 pipeline 判断�?WebSocket 还是 TCP，或者同时支�?
            // 为简单起见，如果�?websocket channel 我们假设使用 TextWebSocketFrame�?
            // 但我们需要知道�?
            // 一个健壮的方法是检�?pipeline 或有一个标志�?
            // 我们假设如果 pipeline �?websocket handler，我们就将其包装�?TextWebSocketFrame 中�?
            
            if (channel.pipeline().get("ws-handler") != null) {
                channel.writeAndFlush(new TextWebSocketFrame(message));
            } else {
                // TCP 字符�?
                channel.writeAndFlush(message + "\n");
            }
        }
    }
}
