package game.engine.gateway.actor;

import game.engine.core.OrionServices;
import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.Props;
import org.apache.pekko.cluster.sharding.ClusterSharding;
import org.apache.pekko.event.Logging;
import org.apache.pekko.event.LoggingAdapter;
import game.engine.player.actor.PlayerActor;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

/**
 * ChannelActor 负责管理单个网络连接（Channel）
 * 它处理来自客户端的入站消息，并将其路由到相应的服务或玩家 Actor
 * 同时它也负责将来自服务器内部的消息发送回客户端
 */
public class ChannelActor extends AbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    private final Channel channel; // 网络连接通道
    private final String gatewayId; // 网关 ID
    private String playerId; // 玩家 ID，登录后绑定
    private long worldId;

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
                .match(game.engine.core.message.Letter.class, this::handleInboundMessage) // 处理来自客户端的
                .match(game.engine.core.message.Envelope.class, this::handleEnvelope) // 处理内部信封消息
                .match(PlayerActor.PlayerMessage.class, this::handleOutboundMessage) // 处理出站玩家消息
                .matchAny(msg -> log.warning("Received unknown message: {} | {}", msg.getClass().getName(), msg))
                .build();
    }

    private void handleEnvelope(game.engine.core.message.Envelope envelope) {
        log.info("ChannelActor received envelope: {}", envelope.getLetter().msgId());
        // TODO: 处理来自其他服务的响应
        // 这里分类型，看是否返回给客户端？ 还是一些状态处理
    }

    private void handleInboundMessage(game.engine.core.message.Letter letter) {
        int msgId = letter.msgId();
        game.engine.gateway.handler.MessageRouter.Destination destination = game.engine.gateway.handler.MessageRouter.route(msgId);
        
        log.info("ChannelActor routing letter ID: {}, Destination: {}", msgId, destination);

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
        int msgId = envelope.getLetter().msgId();
        log.info("Handling Gateway envelope: {}", msgId);

        // 处理登录请求 (ID_LOGIN_REQ = 101)
        if (msgId == 101) {
            // 暂时使用简单的字节转字符串方式获取 playerId
            this.playerId = new String(envelope.getLetter().payload());
            log.info("Player logged in via Packet: {}", playerId);
            
            // 发送欢迎消息给客户端
            sendToClient("Welcome " + playerId + " (via Packet)");
            
            // 通过 Sharding 通知 PlayerActor
            ClusterSharding.get(getContext().getSystem())
                    .shardRegion(PlayerActor.TYPE_NAME)
                    .tell(new PlayerActor.PlayerMessage(playerId, "Login from Gateway via Packet"), getSelf());
        } else {
            log.warning("Unhandled Gateway message ID: {}", msgId);
        }
    }

    private void forwardToHome(game.engine.core.message.Envelope envelope) {
        // TODO: 转发到 Home 服务器
        log.info("Forwarding envelope to Home: {}", envelope.getLetter().msgId());

        ClusterSharding.get(getContext().getSystem())
                .shardRegion(PlayerActor.TYPE_NAME)
                .tell(new PlayerActor.PlayerMessage(playerId, ""), getSelf());
    }

    private void forwardToWorld(game.engine.core.message.Envelope envelope) {
        // TODO: 转发到 World 服务器
        log.info("Forwarding envelope to World: {}", envelope.getLetter().msgId());

        // 转发给 WorldService-{id}
        OrionServices.sendToService(getContext().getSystem(), "WorldService-" + worldId, envelope.getLetter(), getSelf());
    }


    private void handleOutboundMessage(PlayerActor.PlayerMessage msg) {
        sendToClient("Server: " + msg.content);
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

    private void sendToClient(String message) {
        if (channel.isOpen()) {
            // 根据 pipeline 判断是 WebSocket 还是 TCP，或者同时支持
            // 为简单起见，如果是 websocket channel 我们假设使用 TextWebSocketFrame
            // 但我们需要知道：
            // 一个健壮的方法是检查 pipeline 或有一个标志位
            // 我们假设如果 pipeline 有 websocket handler，我们就将其包装在 TextWebSocketFrame 中
            
            if (channel.pipeline().get("ws-handler") != null) {
                channel.writeAndFlush(new TextWebSocketFrame(message));
            } else {
                // TCP 字符串
                channel.writeAndFlush(message + "\n");
            }
        }
    }
}
