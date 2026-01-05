package game.engine.core.actor;

import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import org.apache.pekko.event.Logging;
import org.apache.pekko.event.LoggingAdapter;
import org.apache.pekko.routing.RoundRobinGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * Auth 服务代理（使用 Group Router 负载均衡）
 * 
 * 特性：
 * 1. 使用 Group Router 自动负载均衡到多个 Auth 节点
 * 2. RoundRobin 策略，均匀分配请求
 * 3. 自动发现和连接 Auth 节点
 * 4. 无状态，适合水平扩展
 */
public class AuthServiceProxy extends AbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    private ActorRef authRouter;

    public static Props props() {
        return Props.create(AuthServiceProxy.class, AuthServiceProxy::new);
    }

    @Override
    public void preStart() {
        // 创建 Group Router，连接到所有 auth 角色的节点
        List<String> routeesPaths = new ArrayList<>();
        
        // 路径格式：/user/auth-actor-*
        // 使用集群感知的路径，自动发现所有 auth 节点上的 AuthActor
        String authPath = "/user/auth-actor-*";
        routeesPaths.add(authPath);
        
        // 创建 RoundRobin Group Router
        authRouter = getContext().actorOf(
            new RoundRobinGroup(routeesPaths).props(),
            "auth-group-router"
        );
        
        log.info("AuthServiceProxy started with Group Router: {}", authRouter.path());
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .matchAny(message -> {
                    // 转发所有消息到 Router，由 Router 负载均衡
                    authRouter.forward(message, getContext());
                })
                .build();
    }
}
