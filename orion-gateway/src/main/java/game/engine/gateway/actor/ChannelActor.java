package game.engine.gateway.actor;

import game.engine.core.message.Envelope;
import game.engine.core.message.Letter;
import game.engine.gateway.proto.GatewayProto;
import game.engine.gateway.proto.MsgIdProto;
import game.engine.player.actor.PlayerActor;
import org.apache.pekko.actor.AbstractActorWithStash;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import org.apache.pekko.cluster.sharding.ClusterSharding;
import org.apache.pekko.event.Logging;
import org.apache.pekko.event.LoggingAdapter;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.HashMap;
import java.util.Map;

/**
 * ChannelActor manages a single network connection.
 * State Machine: Authenticating -> LoggedIn -> Running -> Disconnected
 */
public class ChannelActor extends AbstractActorWithStash {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    private final Channel channel;
    private final String gatewayId;
    private long playerId;
    private long accountId; // Store accountId after login
    private final Map<Integer, RateLimiter> rateLimiters = new HashMap<>();

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
        channel.closeFuture().addListener(future -> {
            getSelf().tell(new ConnectionClosed(), ActorRef.noSender());
        });
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
        return authenticating();
    }

    // State: Authenticating
    private Receive authenticating() {
        return receiveBuilder()
                .match(Letter.class, letter -> {
                    int msgId = letter.msgId();
                    if (!checkRateLimit(msgId)) {
                        log.warning("Rate limit exceeded for msgId: {}", msgId);
                        return;
                    }
                    if (msgId == MsgIdProto.MsgId.ID_LOGIN_REQ_VALUE) {
                        handleLoginReq(letter);
                    } else {
                        log.warning("Received message {} while authenticating, ignoring.", msgId);
                    }
                })
                .match(ConnectionClosed.class, msg -> handleConnectionClosed())
                .matchAny(msg -> log.warning("Unknown message in authenticating: {}", msg))
                .build();
    }

    // State: LoggedIn (Auth success, waiting for EnterGame)
    private Receive loggedIn() {
        return receiveBuilder()
                .match(Letter.class, letter -> {
                    int msgId = letter.msgId();
                    if (!checkRateLimit(msgId)) {
                        log.warning("Rate limit exceeded for msgId: {}", msgId);
                        return;
                    }
                    if (msgId == MsgIdProto.MsgId.ID_ENTER_GAME_REQ_VALUE) {
                        handleEnterGameReq(letter);
                    } else {
                        log.warning("Received message {} while loggedIn, expecting EnterGame.", msgId);
                    }
                })
                .match(ConnectionClosed.class, msg -> handleConnectionClosed())
                .matchAny(msg -> log.warning("Unknown message in loggedIn: {}", msg))
                .build();
    }

    // State: Running (Game session active)
    private Receive running() {
        return receiveBuilder()
                .match(Letter.class, this::handleInboundMessage)
                .match(Envelope.class, this::handleEnvelope)
                .match(PlayerActor.PlayerMessage.class, this::handleOutboundMessage)
                .match(ConnectionClosed.class, msg -> handleConnectionClosed())
                .matchAny(msg -> log.warning("Received unknown message in running: {}", msg))
                .build();
    }

    // State: Disconnected (Crashed/Down)
    private Receive disconnected() {
        return receiveBuilder()
                .matchAny(msg -> log.info("ChannelActor is disconnected, ignoring message: {}", msg))
                .build();
    }

    private void handleConnectionClosed() {
        log.info("Channel connection closed. Switching to disconnected state.");
        getContext().become(disconnected());
        getContext().stop(getSelf());
    }

    private void handleLoginReq(Letter letter) {
        try {
            GatewayProto.LoginReq req = GatewayProto.LoginReq.parseFrom(letter.payload());
            log.info("Processing LoginReq: username={}", req.getUsername());

            // TODO: Forward to LoginService. For now, simulate success.
            long simulatedAccountId = Math.abs(req.getUsername().hashCode());
            long simulatedPlayerId = simulatedAccountId;

            this.accountId = simulatedAccountId;
            this.playerId = simulatedPlayerId;

            GatewayProto.LoginResp resp = GatewayProto.LoginResp.newBuilder()
                    .setCode(0)
                    .setMsg("Login Success")
                    .setUid(simulatedPlayerId)
                    .build();

            sendToClient(MsgIdProto.MsgId.ID_LOGIN_RESP_VALUE, resp.toByteArray());

            getContext().become(loggedIn());
            log.info("State switched to LoggedIn. Waiting for EnterGame.");

        } catch (InvalidProtocolBufferException e) {
            log.error(e, "Failed to parse LoginReq");
        }
    }

    private void handleEnterGameReq(Letter letter) {
        try {
            GatewayProto.EnterGameReq req = GatewayProto.EnterGameReq.parseFrom(letter.payload());
            long reqUid = req.getUid();

            if (reqUid != this.playerId) {
                log.warning("EnterGameReq uid {} does not match logged in playerId {}", reqUid, playerId);
                return;
            }

            log.info("Processing EnterGameReq for playerId: {}", playerId);

            // Forward to PlayerActor
            ClusterSharding.get(getContext().getSystem())
                    .shardRegion(PlayerActor.TYPE_NAME)
                    .tell(new PlayerActor.PlayerLoginCommand(reqUid, accountId), getSelf());

            // Wait for EnterGameResp from PlayerActor
            getContext().become(waitingForEnterGameResp());

        } catch (InvalidProtocolBufferException e) {
            log.error(e, "Failed to parse EnterGameReq");
        }
    }

    private Receive waitingForEnterGameResp() {
        return receiveBuilder()
                .match(GatewayProto.EnterGameResp.class, resp -> {
                    log.info("Received EnterGameResp: code={}", resp.getCode());

                    // Forward to client
                    sendToClient(MsgIdProto.MsgId.ID_ENTER_GAME_RESP_VALUE, resp.toByteArray());

                    if (resp.getCode() == 0) {
                        getContext().become(running());
                        log.info("State switched to Running.");
                        unstashAll();
                    } else {
                        log.warning("EnterGame failed, reverting to LoggedIn");
                        getContext().become(loggedIn());
                    }
                })
                .match(ConnectionClosed.class, msg -> handleConnectionClosed())
                .matchAny(msg -> {
                    log.info("Stashing message while waiting for EnterGameResp: {}", msg);
                    stash();
                })
                .build();
    }

    private void handleInboundMessage(Letter letter) {
        int msgId = letter.msgId();
        if (!checkRateLimit(msgId)) {
            log.warning("Rate limit exceeded for msgId: {}", msgId);
            return;
        }
        game.engine.gateway.handler.MessageRouter.Destination destination = game.engine.gateway.handler.MessageRouter
                .route(msgId);
        // Envelope envelope = new Envelope(letter, playerId, gatewayId);

        switch (destination) {
            case HOME:
                log.info("Forwarding to Home: {}", msgId);
                ClusterSharding.get(getContext().getSystem())
                        .shardRegion(PlayerActor.TYPE_NAME)
                        .tell(new PlayerActor.PlayerMessage(playerId, new String(letter.payload())), getSelf());
                break;
            case WORLD:
                log.info("Forwarding to World: {}", msgId);
                // Assuming WorldService is available via OrionServices or similar
                break;
            default:
                // Forward to PlayerActor
                ClusterSharding.get(getContext().getSystem())
                        .shardRegion(PlayerActor.TYPE_NAME)
                        .tell(new PlayerActor.PlayerMessage(playerId, new String(letter.payload())), getSelf());
                break;
        }
    }

    private void handleEnvelope(Envelope envelope) {
        // Handle responses from other services
    }

    private void handleOutboundMessage(PlayerActor.PlayerMessage msg) {
        sendToClient(0, msg.content.getBytes());
    }

    private void sendToClient(int msgId, byte[] payload) {
        if (channel.isOpen()) {
            String content = msgId + "|" + java.util.Base64.getEncoder().encodeToString(payload);
            channel.writeAndFlush(new TextWebSocketFrame(content));
        }
    }

    private static class ConnectionClosed {
    }

    private static class RateLimiter {
        int count;
        long lastResetTime;
        final int limit;

        RateLimiter(int limit) {
            this.limit = limit;
            this.lastResetTime = System.currentTimeMillis();
        }

        boolean tryAcquire() {
            long now = System.currentTimeMillis();
            if (now - lastResetTime > 1000) {
                count = 0;
                lastResetTime = now;
            }
            if (count < limit) {
                count++;
                return true;
            }
            return false;
        }
    }

    private boolean checkRateLimit(int msgId) {
        MsgIdProto.MsgId msgIdEnum = MsgIdProto.MsgId.forNumber(msgId);
        if (msgIdEnum == null)
            return true;

        if (!msgIdEnum.getValueDescriptor().getOptions().hasExtension(MsgIdProto.rateLimit)) {
            return true;
        }

        int limit = msgIdEnum.getValueDescriptor().getOptions().getExtension(MsgIdProto.rateLimit);
        if (limit <= 0)
            return true;

        RateLimiter limiter = rateLimiters.computeIfAbsent(msgId, k -> new RateLimiter(limit));
        return limiter.tryAcquire();
    }
}
