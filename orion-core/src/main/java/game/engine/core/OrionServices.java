package game.engine.core;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.cluster.pubsub.DistributedPubSub;
import org.apache.pekko.cluster.pubsub.DistributedPubSubMediator;

public class OrionServices {

    public static void registerService(ActorSystem system, String serviceName, ActorRef serviceActor) {
        ActorRef mediator = DistributedPubSub.get(system).mediator();
        mediator.tell(new DistributedPubSubMediator.Put(serviceActor), serviceActor);
        // 如果需要广播，也可以订阅服务名称的主题
        mediator.tell(new DistributedPubSubMediator.Subscribe(serviceName, serviceActor), serviceActor);
    }

    public static void sendToService(ActorSystem system, String serviceName, Object message, ActorRef sender) {
        ActorRef mediator = DistributedPubSub.get(system).mediator();
        // 发送到服务的一个实例（如果存在多个，由 mediator 随机选择）
        // 我们假设服务 actor 已使用其路径注册，或者我们使用主题。
        // 对于 Put/Send，我们需要路径。如果我们使用 Subscribe/Publish，它是广播。
        // 要发送给任何单个实例，Send 更好，但要求 actor 做 Put。
        // Put 中的路径通常是 self.path().withoutAddress()。
        
        // 此框架的简化方法：使用 Publish 到主题，但这会广播到组。
        // 更好：使用带路径的 Send。但如果是动态的，我们不知道路径。
        // 让我们使用一致的命名约定或仅使用 Group Router。
        
        // 实际上，让我们使用 DistributedPubSub 的 SendToAll 或仅 Send（如果我们知道路径）。
        // 但如果是动态的， we 不知道路径。
        
        // 替代方案：用于单例的 ClusterSingleton。
        // 对于示例，让我们使用带有主题的 DistributedPubSub 并发送给一个。
        // DistributedPubSub 不容易支持“发送给主题的一个”而不需要组。
        
        // 让我们使用带有众所周知路径约定的 Send？不，那很脆弱。
        
        // 让我们使用基于“角色”的路由或仅用于示例的简单 ActorSelection。
        // 或者更好，实现计划中提到的“ServiceProxy”，使用 Group Router。
        
        // 为了在此迭代中保持简单，我们只公开 Mediator 并让用户使用它，
        // 或者提供一个助手发送到“服务主题”。
        
        mediator.tell(new DistributedPubSubMediator.Publish(serviceName, message), sender);
    }
}
