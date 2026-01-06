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
 * Portal 服务代理（使用 Group Router 负载均衡）
 * 
 * 特性：
 * 1. 使用 Group Router 自动负载均衡到多个 Portal 节点
 * 2. RoundRobin 策略，均匀分配请求
 * 3. 自动发现和连接 Portal 节点
 * 4. 无状态，适合水平扩展
 */
public class PortalServiceProxy extends AbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    private ActorRef portalRouter;

    public static Props props() {
        return Props.create(PortalServiceProxy.class, PortalServiceProxy::new);
    }

    @Override
    public void preStart() {
        // 创建 Group Router，连接到所有 portal 角色的节点
        List<String> routeesPaths = new ArrayList<>();
        
        // 路径格式：/user/portal-actor-*
        // 使用集群感知的路径，自动发现所有 portal 节点上的 PortalActor
        String portalPath = "/user/portal-actor-*";
        routeesPaths.add(portalPath);
        
        // 创建 RoundRobin Group Router
        portalRouter = getContext().actorOf(
            new RoundRobinGroup(routeesPaths).props(),
            "portal-group-router"
        );
        
        log.info("PortalServiceProxy started with Group Router: {}", portalRouter.path());
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .matchAny(message -> {
                    // 转发所有消息到 Router，由 Router 负载均衡
                    portalRouter.forward(message, getContext());
                })
                .build();
    }
}
