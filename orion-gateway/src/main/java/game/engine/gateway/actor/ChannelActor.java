package game.engine.gateway.actor;

import game.engine.core.actor.AuthMessages;
import game.engine.core.actor.PlayerMessages;
import game.engine.core.actor.PlayerShardingConfig;
import game.engine.core.actor.WorldMessages;
import game.engine.core.actor.WorldServiceProxy;
import game.engine.core.OrionServices;
import game.engine.core.message.Envelope;
import game.engine.core.message.Letter;
import game.engine.gateway.proto.GatewayProto;
import game.engine.gateway.proto.MsgIdProto;
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
 * ChannelActor 管理单个网络连接。
 * 该Actor使用有限状态机（FSM）模式管理连接的整个生命周期。
 * 状态转换流程：认证中 -> 已登录 -> 等待进入游戏响应 -> 运行中 -> 已断开
 * 
 * 主要功能：
 * 1. 处理客户端登录认证
 * 2. 管理进入游戏流程
 * 3. 在运行状态下转发客户端和服务器之间的消息
 * 4. 实现消息频率限制以防止滥用
 * 5. 管理连接生命周期和状态转换
 */
public class ChannelActor
        extends org.apache.pekko.actor.AbstractFSMWithStash<ChannelActor.ChannelState, ChannelActor.ChannelData> {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    private final Channel channel;
    private final Map<Integer, RateLimiter> rateLimiters = new HashMap<>();
    
    // Auth 服务代理引用（Group Router 负载均衡）
    private ActorRef authServiceProxy;
    
    // World 服务代理引用
    private ActorRef worldServiceProxy;

    // FSM 状态定义
    public enum ChannelState {
        AUTHENTICATING, // 认证中 - 初始状态，等待登录请求
        LOGGED_IN, // 已登录 - 认证成功，等待进入游戏请求
        WAITING_FOR_ENTER_GAME_RESP, // 等待进入游戏响应 - 已发送请求给 PlayerActor，等待响应
        RUNNING, // 运行中 - 游戏会话激活，处理玩家游戏消息
        DISCONNECTED // 已断开 - 连接已关闭
    }

    // FSM 数据定义，存储跨状态的上下文信息
    public record ChannelData(long uid, long accountId, int worldId) {
        public ChannelData() {
            this(0, 0, 1); // 默认世界ID为1
        }

        public ChannelData withUid(long uid) {
            // 创建一个新的ChannelData实例，具有指定的uid
            return new ChannelData(uid, this.accountId, this.worldId);
        }

        public ChannelData withAccountId(long accountId) {
            // 创建一个新的ChannelData实例，具有指定的accountId
            return new ChannelData(this.uid, accountId, this.worldId);
        }

        public ChannelData withWorldId(int worldId) {
            // 创建一个新的ChannelData实例，具有指定的worldId
            return new ChannelData(this.uid, this.accountId, worldId);
        }
    }

    public ChannelActor(Channel channel) {
        this.channel = channel;

        // 初始状态：认证中
        startWith(ChannelState.AUTHENTICATING, new ChannelData());

        // 状态处理：认证中 - 只处理登录请求
        when(ChannelState.AUTHENTICATING,
                matchEvent(Letter.class, (letter, data) -> {
                    int msgId = letter.msgId();
                    // 频率限制检查 - 防止恶意请求
                    if (!checkRateLimit(msgId)) {
                        log.warning("Rate limit exceeded for msgId: {}", msgId);
                        return stay();
                    }
                    // 仅允许登录请求 - 其他消息将被忽略
                    if (msgId == MsgIdProto.MsgId.ID_LOGIN_REQ_VALUE) {
                        return handleLoginReq(letter);
                    } else {
                        log.warning("Received message {} while authenticating, ignoring.", msgId);
                        return stay();
                    }
                }));

        // State: LOGGED_IN - 已认证，等待进入游戏请求
        when(ChannelState.LOGGED_IN,
                matchEvent(Letter.class, (letter, data) -> {
                    int msgId = letter.msgId();
                    // 频率限制检查 - 防止恶意请求
                    if (!checkRateLimit(msgId)) {
                        log.warning("Rate limit exceeded for msgId: {}", msgId);
                        return stay();
                    }
                    // 只处理进入游戏请求 - 其他消息将被忽略
                    if (msgId == MsgIdProto.MsgId.ID_ENTER_GAME_REQ_VALUE) {
                        return handleEnterGameReq(letter, data);
                    } else {
                        log.warning("Received message {} while loggedIn, expecting EnterGame.", msgId);
                        return stay();
                    }
                }));

        // 状态处理：等待进入游戏响应
        when(ChannelState.WAITING_FOR_ENTER_GAME_RESP,
                matchEvent(GatewayProto.EnterGameResp.class, (resp, data) -> {
                    log.info("Received EnterGameResp: code={}", resp.getCode());
                    // 转发响应给客户端
                    sendToClient(MsgIdProto.MsgId.ID_ENTER_GAME_RESP_VALUE, resp.toByteArray());

                    if (resp.getCode() == 0) {
                        log.info("State switched to Running.");
                        return goTo(ChannelState.RUNNING);
                    } else {
                        log.warning("EnterGame failed, reverting to LoggedIn");
                        return goTo(ChannelState.LOGGED_IN);
                    }
                }).anyEvent((event, data) -> {
                    // 等待响应期间，暂存其他消息
                    log.info("Stashing message while waiting for EnterGameResp: {}", event);
                    stash();
                    return stay();
                }));

        // State: RUNNING - 游戏会话激活状态，处理各种消息类型
        when(ChannelState.RUNNING,
                matchAnyEvent((event, data) -> {
                    if (event instanceof Letter) {
                        // 处理客户端发来的游戏消息
                        handleInboundMessage((Letter) event, data);
                    } else if (event instanceof game.engine.core.message.Envelope) {
                        // 处理来自其他服务的响应消息
                        handleEnvelope((game.engine.core.message.Envelope) event);
                    } else if (event instanceof PlayerMessages.PlayerMessage) {
                        // 处理来自PlayerActor的出站消息（服务器推送给客户端）
                        handleOutboundMessage((PlayerMessages.PlayerMessage) event);
                    }
                    return stay();
                }));

        // State: DISCONNECTED
        when(ChannelState.DISCONNECTED,
                matchAnyEvent((event, data) -> {
                    log.info("ChannelActor is disconnected, ignoring message: {}", event);
                    return stay();
                }));

        // Common Transitions
        whenUnhandled(
                matchEvent(ConnectionClosed.class, (msg, data) -> handleConnectionClosed())
                        .anyEvent((event, data) -> {
                            log.warning("Received unhandled event {} in state {}", event, stateName());
                            return stay();
                        }));

        onTransition(
                matchState(null, null, (from, to) -> {
                    log.info("Transition from {} to {}", from, to);
                    // 从等待进入游戏响应状态转换到运行状态时，取消暂存所有消息
                    if (from == ChannelState.WAITING_FOR_ENTER_GAME_RESP && to == ChannelState.RUNNING) {
                        unstashAll(); // 处理在等待响应期间暂存的消息
                    }
                }));

        initialize();
    }

    public static Props props(Channel channel) {
        // 创建ChannelActor的Props实例
        return Props.create(ChannelActor.class, () -> new ChannelActor(channel));
    }

    @Override
    public void preStart() throws Exception {
        log.info("ChannelActor started for channel: {}", channel.id());
        
        // 查找 Auth 服务代理（Group Router）
        scala.concurrent.Future<ActorRef> authFuture = getContext().getSystem()
            .actorSelection(OrionServices.AUTH_SERVICE_PROXY_PATH)
            .resolveOne(scala.concurrent.duration.Duration.create(3, java.util.concurrent.TimeUnit.SECONDS));
        authServiceProxy = scala.concurrent.Await.result(authFuture, 
            scala.concurrent.duration.Duration.create(3, java.util.concurrent.TimeUnit.SECONDS));
        
        // 查找 World 服务代理
        scala.concurrent.Future<ActorRef> worldFuture = getContext().getSystem()
            .actorSelection(OrionServices.WORLD_SERVICE_PROXY_PATH)
            .resolveOne(scala.concurrent.duration.Duration.create(3, java.util.concurrent.TimeUnit.SECONDS));
        worldServiceProxy = scala.concurrent.Await.result(worldFuture,
            scala.concurrent.duration.Duration.create(3, java.util.concurrent.TimeUnit.SECONDS));
        
        // 监听连接关闭事件，连接关闭时发送ConnectionClosed消息给自己
        channel.closeFuture().addListener(future -> {
            getSelf().tell(new ConnectionClosed(), ActorRef.noSender());
        });
    }

    @Override
    public void postStop() {
        log.info("ChannelActor stopped for channel: {}", channel.id());
        // 确保连接被关闭
        if (channel.isOpen()) {
            channel.close();
        }
    }

    private State<ChannelState, ChannelData> handleConnectionClosed() {
        log.info("Channel connection closed. Switching to disconnected state.");
        // 停止当前Actor
        getContext().stop(getSelf());
        return goTo(ChannelState.DISCONNECTED);
    }

    private State<ChannelState, ChannelData> handleLoginReq(Letter letter) {
        try {
            GatewayProto.LoginReq req = GatewayProto.LoginReq.parseFrom(letter.payload());
            log.info("Processing LoginReq: username={}", req.getUsername());

            // 模拟账号ID和玩家ID生成（实际应用中应从认证服务获取）
            long simulatedAccountId = Math.abs(req.getUsername().hashCode());
            long simulatedPlayerId = simulatedAccountId;
            int simulatedWorldId = (int) simulatedAccountId;

            GatewayProto.LoginResp resp = GatewayProto.LoginResp.newBuilder()
                    .setCode(0) // 0表示成功
                    .setMsg("Login Success")
                    .setUid(simulatedPlayerId)
                    .build();

            // 发送登录响应给客户端
            sendToClient(MsgIdProto.MsgId.ID_LOGIN_RESP_VALUE, resp.toByteArray());

            log.info("State switched to LoggedIn. Waiting for EnterGame.");
            // 转换到已登录状态，并存储uid和账号ID
            return goTo(ChannelState.LOGGED_IN).using(new ChannelData(simulatedPlayerId, simulatedAccountId, simulatedWorldId));

        } catch (InvalidProtocolBufferException e) {
            log.error(e, "Failed to parse LoginReq");
            return stay();
        }
    }

    private State<ChannelState, ChannelData> handleEnterGameReq(Letter letter, ChannelData data) {
        try {
            GatewayProto.EnterGameReq req = GatewayProto.EnterGameReq.parseFrom(letter.payload());
            long reqUid = req.getUid();

            // 验证请求的UID与已登录的uid是否匹配
            if (reqUid != data.uid) {
                log.warning("EnterGameReq uid {} does not match logged in uid {}", reqUid, data.uid);
                return stay();
            }

            log.info("Processing EnterGameReq for uid: {}", data.uid);

            // 转发进入游戏请求到对应的PlayerActor
            ClusterSharding.get(getContext().getSystem())
                    .shardRegion(PlayerShardingConfig.TYPE_NAME)
                    .tell(new PlayerMessages.PlayerLoginCommand(reqUid, data.accountId), getSelf());

            // 转换到等待响应状态
            return goTo(ChannelState.WAITING_FOR_ENTER_GAME_RESP);

        } catch (InvalidProtocolBufferException e) {
            log.error(e, "Failed to parse EnterGameReq");
            return stay();
        }
    }

    private void handleInboundMessage(Letter letter, ChannelData data) {
        int msgId = letter.msgId();
        // 检查频率限制
        if (!checkRateLimit(msgId)) {
            log.warning("Rate limit exceeded for msgId: {}", msgId);
            return;
        }
        // 根据消息ID确定路由目的地
        game.engine.gateway.handler.MessageRouter.Destination destination = game.engine.gateway.handler.MessageRouter
                .route(msgId);

        switch (destination) {
            case HOME:
                log.info("Forwarding to Home: {}", msgId);
                // 转发到PlayerActor（HOME服务）
                ClusterSharding.get(getContext().getSystem())
                        .shardRegion(PlayerShardingConfig.TYPE_NAME)
                        .tell(new PlayerMessages.PlayerMessage(data.uid, new String(letter.payload())), getSelf());
                break;
            case WORLD:
                log.info("Forwarding to World: {}", msgId);
                // 使用 WorldServiceProxy 转发（高性能 + 自动处理死信）
                worldServiceProxy.tell(
                    new WorldServiceProxy.ForwardToWorld(
                        data.worldId,
                        new WorldMessages.WorldMessage(data.uid, data.worldId, new String(letter.payload())),
                        getSelf()
                    ),
                    getSelf()
                );
                break;
            default:
                // 默认转发到PlayerActor
                ClusterSharding.get(getContext().getSystem())
                        .shardRegion(PlayerShardingConfig.TYPE_NAME)
                        .tell(new PlayerMessages.PlayerMessage(data.uid, new String(letter.payload())), getSelf());
                break;
        }
    }

    private void handleEnvelope(Envelope envelope) {
        // 处理来自其他服务的响应消息
        // TODO: 实现处理来自其他服务的响应消息逻辑
    }

    private void handleOutboundMessage(PlayerMessages.PlayerMessage msg) {
        // 处理来自PlayerActor的出站消息（服务器推送给客户端）
        sendToClient(0, msg.content.getBytes());
    }

    private void sendToClient(int msgId, byte[] payload) {
        // 检查通道是否仍然打开
        if (channel.isOpen()) {
            // 构建消息内容：消息ID + 分隔符 + Base64编码的负载
            String content = msgId + "|" + java.util.Base64.getEncoder().encodeToString(payload);
            // 通过WebSocket发送消息
            channel.writeAndFlush(new TextWebSocketFrame(content));
        }
    }

    private static class ConnectionClosed {
    }

    private static class RateLimiter {
        int count; // 当前计数
        long lastResetTime; // 上次重置时间
        final int limit; // 限制数量

        RateLimiter(int limit) {
            this.limit = limit;
            this.lastResetTime = System.currentTimeMillis();
        }

        boolean tryAcquire() {
            long now = System.currentTimeMillis();
            // 每秒重置计数器
            if (now - lastResetTime > 1000) {
                count = 0;
                lastResetTime = now;
            }
            // 检查是否还可以获取许可
            if (count < limit) {
                count++;
                return true; // 获取许可成功
            }
            return false; // 获取许可失败
        }
    }

    private boolean checkRateLimit(int msgId) {
        // 根据消息ID获取对应的枚举值
        MsgIdProto.MsgId msgIdEnum = MsgIdProto.MsgId.forNumber(msgId);
        if (msgIdEnum == null)
            return true; // 未知消息ID，不限制

        // 检查该消息ID是否配置了频率限制
        if (!msgIdEnum.getValueDescriptor().getOptions().hasExtension(MsgIdProto.rateLimit)) {
            return true; // 未配置频率限制，不限制
        }

        // 获取频率限制值
        int limit = msgIdEnum.getValueDescriptor().getOptions().getExtension(MsgIdProto.rateLimit);
        if (limit <= 0)
            return true; // 限制值小于等于0，不限制

        // 获取或创建对应的限流器，并尝试获取许可
        RateLimiter limiter = rateLimiters.computeIfAbsent(msgId, k -> new RateLimiter(limit));
        return limiter.tryAcquire();
    }
}