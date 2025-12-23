package game.engine.core;

import org.apache.pekko.dispatch.DispatcherPrerequisites;
import org.apache.pekko.dispatch.ExecutorServiceConfigurator;
import org.apache.pekko.dispatch.ExecutorServiceFactory;
import com.typesafe.config.Config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class VirtualThreadExecutorConfigurator extends ExecutorServiceConfigurator {

    public VirtualThreadExecutorConfigurator(Config config, DispatcherPrerequisites prerequisites) {
        super(config, prerequisites);
    }

    @Override
    public ExecutorServiceFactory createExecutorServiceFactory(String id, ThreadFactory threadFactory) {
        return new ExecutorServiceFactory() {
            @Override
            public ExecutorService createExecutorService() {
                // 使用 Java 21 �?newVirtualThreadPerTaskExecutor
                return Executors.newVirtualThreadPerTaskExecutor();
            }
        };
    }
}
