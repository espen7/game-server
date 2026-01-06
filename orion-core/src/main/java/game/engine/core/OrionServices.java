package game.engine.core;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.cluster.pubsub.DistributedPubSub;
import org.apache.pekko.cluster.pubsub.DistributedPubSubMediator;
import org.apache.pekko.routing.FromConfig;
import org.apache.pekko.routing.RoundRobinGroup;
import scala.concurrent.duration.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class OrionServices {

    // 服务代理 Actor 路径常量
    public static final String PORTAL_SERVICE_PROXY_NAME = "portal-service-proxy";
    public static final String PORTAL_SERVICE_PROXY_PATH = "/user/" + PORTAL_SERVICE_PROXY_NAME;
    
    public static final String WORLD_SERVICE_PROXY_NAME = "world-service-proxy";
    public static final String WORLD_SERVICE_PROXY_PATH = "/user/" + WORLD_SERVICE_PROXY_NAME;

    /**
     * 注册服务到 DistributedPubSub
     * 使 Actor 可以被跨节点的 Send 消息找到
     * 
     * @param system ActorSystem
     * @param serviceName 服务名称（用于日志）
     * @param serviceActor 服务 Actor 引用
     */
    public static void registerService(ActorSystem system, String serviceName, ActorRef serviceActor) {
        ActorRef mediator = DistributedPubSub.get(system).mediator();
        // 使用 Put 注册 Actor，这样可以通过 Send 发送消息
        // Put 会自动使用 actor.path().toStringWithoutAddress() 作为路径
        mediator.tell(new DistributedPubSubMediator.Put(serviceActor), serviceActor);
    }

    /**
     * 发送消息到指定服务（点对点）
     * 使用 DistributedPubSub 的 Send 机制
     * 
     * @param system ActorSystem
     * @param servicePath 服务路径（例如：/user/world-1）
     * @param message 要发送的消息
     * @param sender 发送者引用
     */
    public static void sendToService(ActorSystem system, String servicePath, Object message, ActorRef sender) {
        ActorRef mediator = DistributedPubSub.get(system).mediator();
        // 使用 Send：发送给指定路径的单个 Actor
        mediator.tell(new DistributedPubSubMediator.Send(servicePath, message, false), sender);
    }

    /**
     * 广播消息到所有订阅该主题的服务
     * 
     * @param system ActorSystem
     * @param topic 主题名称
     * @param message 要发送的消息
     * @param sender 发送者引用
     */
    public static void publishToTopic(ActorSystem system, String topic, Object message, ActorRef sender) {
        ActorRef mediator = DistributedPubSub.get(system).mediator();
        // 使用 Publish：广播给所有订阅者
        mediator.tell(new DistributedPubSubMediator.Publish(topic, message), sender);
    }

    /**
     * 订阅主题
     * 订阅后，该 Actor 会接收到所有发布到该主题的消息
     * 
     * @param system ActorSystem
     * @param topic 主题名称
     * @param subscriber 订阅者 Actor 引用
     */
    public static void subscribeTopic(ActorSystem system, String topic, ActorRef subscriber) {
        ActorRef mediator = DistributedPubSub.get(system).mediator();
        // 使用 Subscribe：订阅主题
        mediator.tell(new DistributedPubSubMediator.Subscribe(topic, subscriber), subscriber);
    }

    /**
     * 取消订阅主题
     * 
     * @param system ActorSystem
     * @param topic 主题名称
     * @param subscriber 订阅者 Actor 引用
     */
    public static void unsubscribeTopic(ActorSystem system, String topic, ActorRef subscriber) {
        ActorRef mediator = DistributedPubSub.get(system).mediator();
        // 使用 Unsubscribe：取消订阅主题
        mediator.tell(new DistributedPubSubMediator.Unsubscribe(topic, subscriber), subscriber);
    }

    /**
     * 查找服务 Actor 引用
     * 使用 ActorSelection 通过服务名称查找
     * 
     * @param system ActorSystem
     * @param serviceName 服务名称（Actor 名称）
     * @return CompletableFuture<ActorRef> 查找到的 ActorRef，如果不存在则为 null
     */
    public static CompletableFuture<ActorRef> lookupService(ActorSystem system, String serviceName) {
        CompletableFuture<ActorRef> future = new CompletableFuture<>();
        
        // 使用 ActorSelection 查找服务
        // 路径格式: /user/{actorName}
        String actorPath = "/user/" + serviceName;
        
        system.actorSelection(actorPath)
            .resolveOne(Duration.create(3, TimeUnit.SECONDS))
            .onComplete(result -> {
                if (result.isSuccess()) {
                    future.complete(result.get());
                } else {
                    future.complete(null);
                }
                return null;
            }, system.dispatcher());
        
        return future;
    }

    /**
     * 创建集群感知的 Group Router
     * 用于高性能的点对点通信，自动处理节点故障和重连
     * 
     * @param system ActorSystem
     * @param role 目标节点角色（例如："world"）
     * @param actorPath Actor 路径（例如："/user/world-*"，支持通配符）
     * @return ActorRef Router 引用
     */
    public static ActorRef createClusterRouter(ActorSystem system, String role, String actorPath) {
        // 构建集群路径列表
        List<String> routeesPaths = new ArrayList<>();
        // 格式：pekko://ClusterSystem@{role}/user/world-*
        String clusterPath = String.format("pekko://%s@%s%s", 
            system.name(), role, actorPath);
        routeesPaths.add(clusterPath);
        
        // 创建 Group Router（使用 RoundRobin 策略）
        return system.actorOf(
            new RoundRobinGroup(routeesPaths).props(),
            "cluster-router-" + role + "-" + System.currentTimeMillis()
        );
    }
}
