package game.engine.core.hotfix;

import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Cancellable;
import org.apache.pekko.actor.Props;
import org.apache.pekko.cluster.pubsub.DistributedPubSub;
import org.apache.pekko.cluster.pubsub.DistributedPubSubMediator;
import org.apache.pekko.event.Logging;
import org.apache.pekko.event.LoggingAdapter;
import scala.concurrent.duration.Duration;

import java.io.File;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

/**
 * 监听文件变动�?Actor�?
 * 定期轮询 WatchService 以检�?.class 文件的变化�?
 */
public class FileWatcherActor extends AbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    private final String watchDir;
    private WatchService watchService;
    private Cancellable checkTask;

    public static class Check {}
    
    public static class HotfixEvent {
        public final long timestamp;
        public HotfixEvent() {
            this.timestamp = System.currentTimeMillis();
        }
    }

    public static Props props(String watchDir) {
        return Props.create(FileWatcherActor.class, () -> new FileWatcherActor(watchDir));
    }

    public FileWatcherActor(String watchDir) {
        this.watchDir = watchDir;
    }

    @Override
    public void preStart() throws Exception {
        Path path = Paths.get(watchDir);
        File file = path.toFile();
        if (!file.exists()) {
            file.mkdirs();
        }

        watchService = FileSystems.getDefault().newWatchService();
        path.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);
        
        log.info("FileWatcherActor started, watching: {}", path.toAbsolutePath());

        // 每秒检查一�?
        checkTask = getContext().getSystem().scheduler().scheduleWithFixedDelay(
                Duration.create(1, TimeUnit.SECONDS),
                Duration.create(1, TimeUnit.SECONDS),
                getSelf(),
                new Check(),
                getContext().getDispatcher(),
                getSelf()
        );
    }

    @Override
    public void postStop() throws Exception {
        if (checkTask != null) {
            checkTask.cancel();
        }
        if (watchService != null) {
            watchService.close();
        }
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(Check.class, msg -> checkChanges())
                .build();
    }

    private void checkChanges() {
        if (watchService == null) return;

        WatchKey key = watchService.poll(); // 非阻塞获�?
        if (key != null) {
            boolean hasChanges = false;
            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                WatchEvent<Path> ev = (WatchEvent<Path>) event;
                Path filename = ev.context();

                if (filename.toString().endsWith(".class")) {
                    log.info("Detected change in file: {}", filename);
                    hasChanges = true;
                }
            }

            if (hasChanges) {
                triggerHotfix();
            }

            boolean valid = key.reset();
            if (!valid) {
                log.warning("WatchKey no longer valid, stopping watcher.");
                checkTask.cancel();
            }
        }
    }

    private void triggerHotfix() {
        log.info("Triggering hotfix...");
        // 发布热更事件
        ActorRef mediator = DistributedPubSub.get(getContext().getSystem()).mediator();
        mediator.tell(new DistributedPubSubMediator.Publish("Hotfix", new HotfixEvent()), getSelf());
    }
}
