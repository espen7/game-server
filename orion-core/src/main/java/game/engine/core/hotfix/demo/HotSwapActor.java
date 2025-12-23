package game.engine.core.hotfix.demo;

import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import org.apache.pekko.cluster.pubsub.DistributedPubSub;
import org.apache.pekko.cluster.pubsub.DistributedPubSubMediator;
import org.apache.pekko.event.Logging;
import org.apache.pekko.event.LoggingAdapter;
import game.engine.core.hotfix.FileWatcherActor;
import game.engine.core.hotfix.HotfixClassLoader;

import java.io.File;

public class HotSwapActor extends AbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    private LogicStrategy strategy;
    private final String hotfixDir;

    public HotSwapActor(String hotfixDir) {
        this.hotfixDir = hotfixDir;
        this.strategy = new DefaultLogic();
    }

    public static Props props(String hotfixDir) {
        return Props.create(HotSwapActor.class, () -> new HotSwapActor(hotfixDir));
    }

    @Override
    public void preStart() {
        // 订阅热更事件
        ActorRef mediator = DistributedPubSub.get(getContext().getSystem()).mediator();
        mediator.tell(new DistributedPubSubMediator.Subscribe("Hotfix", getSelf()), getSelf());
        log.info("HotSwapActor started with strategy: {}", strategy.getClass().getName());
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(String.class, msg -> {
                    log.info("Result: {}", strategy.execute(msg));
                })
                .match(FileWatcherActor.HotfixEvent.class, msg -> {
                    log.info("Received HotfixEvent, reloading logic...");
                    reloadLogic();
                })
                .match(DistributedPubSubMediator.SubscribeAck.class, msg -> {
                    log.info("Subscribed to Hotfix topic");
                })
                .build();
    }

    private void reloadLogic() {
        try {
            // 创建新的 ClassLoader
            HotfixClassLoader loader = HotfixClassLoader.create(hotfixDir, getClass().getClassLoader());
            
            // 加载新的逻辑�?(假设类名不变，或者是配置指定�?
            // 这里为了演示，我们假设要加载的是 DefaultLogic 的新版本，或者一个特定的实现�?
            // 在实际项目中，通常通过配置文件或约定来确定要加载哪个类
            Class<?> clazz = loader.loadClass("game.engine.core.hotfix.demo.DefaultLogic");
            
            // 实例化并替换
            Object newInstance = clazz.getDeclaredConstructor().newInstance();
            if (newInstance instanceof LogicStrategy) {
                this.strategy = (LogicStrategy) newInstance;
                log.info("Logic reloaded successfully! New strategy: {}", strategy.getClass().getName());
                log.info("Test execution: {}", strategy.execute("Test"));
            } else {
                log.error("Loaded class does not implement LogicStrategy");
            }
            
        } catch (Exception e) {
            log.error(e, "Failed to reload logic");
        }
    }
}
