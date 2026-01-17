package game.engine.core.server;

import game.engine.core.OrionEngine;
import game.engine.core.ProcessType;
import game.engine.core.bootstrap.Bootstrap;
import game.engine.core.bootstrap.BootstrapContext;
import game.engine.core.bootstrap.BootstrapException;
import game.engine.core.bootstrap.BootstrapManager;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.CoordinatedShutdown;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Abstract base class for all game servers.
 * 
 * <p>封装通用的服务器启动逻辑，支持基于 Bootstrap 的组件化启动架构。
 * 
 * <h2>启动流程</h2>
 * <ol>
 *   <li>解析命令行参数</li>
 *   <li>创建 ActorSystem</li>
 *   <li>注册 Bootstrap 组件（子类通过 {@link #registerBootstraps(BootstrapManager)} 实现）</li>
 *   <li>初始化所有 Bootstrap</li>
 *   <li>注册关闭钩子</li>
 * </ol>
 * 
 * <h2>使用示例</h2>
 * <pre>
 * public class MyServer extends AbstractServer {
 *     &#64;Override
 *     protected ProcessType getProcessType() {
 *         return ProcessType.GATEWAY;
 *     }
 *     
 *     &#64;Override
 *     protected void registerBootstraps(BootstrapManager manager) {
 *         manager.register(new ChannelBootstrap());
 *         manager.register(new NettyBootstrap());
 *     }
 * }
 * </pre>
 * 
 * <p>所有启动逻辑都应通过 Bootstrap 组件实现，不再支持直接覆盖启动方法。
 */
public abstract class AbstractServer {

    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected ActorSystem system;
    protected int instanceId;
    protected BootstrapManager bootstrapManager;

    /**
     * Entry point for the server.
     * 
     * @param args Command line arguments
     */
    public void boot(String[] args) {
        // 1. Parse Instance ID
        this.instanceId = parseInstanceId(args);

        // 2. Determine Process Type
        ProcessType processType = getProcessType();

        logger.info("Starting {} with instanceId: {}", processType, instanceId);

        // 3. Create OrionEngine
        OrionEngine engine = OrionEngine.create()
                .withProcessType(processType)
                .withPort(processType.getPort(instanceId));

        // 4. Configure Seed Nodes
        if (shouldJoinCluster()) {
            engine.withDefaultSeedNode();
        }

        // 5. Start ActorSystem
        this.system = engine.start();
        logger.info("ActorSystem created: {}, instance: {}, port: {}",
                system.name(), instanceId, processType.getPort(instanceId));

        // 6. Initialize Bootstrap Components
        try {
            initializeBootstraps();
        } catch (BootstrapException e) {
            logger.error("Failed to initialize bootstraps", e);
            system.terminate();
            System.exit(1);
        }

        // 7. Register CoordinatedShutdown Tasks
        // Pekko 默认会在 JVM shutdown 时自动触发 CoordinatedShutdown (run-by-jvm-shutdown-hook = on)
        // 我们只需要注册自定义的关闭任务到各个阶段即可
        registerCoordinatedShutdown(processType);
        
        logger.info("{} started successfully. CoordinatedShutdown is ready.", processType);
    }

    /**
     * 初始化 Bootstrap 组件
     */
    private void initializeBootstraps() throws BootstrapException {
        BootstrapContext context = BootstrapContext.builder(system)
                .instanceId(instanceId)
                .processType(getProcessType().name())
                .build();
        
        this.bootstrapManager = new BootstrapManager(context);
        
        // 子类注册 Bootstrap
        registerBootstraps(bootstrapManager);
        
        // 初始化所有 Bootstrap
        if (bootstrapManager.getBootstrapCount() > 0) {
            logger.info("Registered {} bootstraps: {}", 
                    bootstrapManager.getBootstrapCount(), 
                    bootstrapManager.getBootstrapNames());
            bootstrapManager.initAll();
        } else {
            logger.info("No bootstraps registered");
        }
    }
    
    /**
     * 注册协调关闭任务到 Pekko CoordinatedShutdown
     * 
     * <p>关闭阶段说明（按执行顺序）:
     * <ul>
     *   <li>service-unbind: 停止接受新连接（Netty 等）</li>
     *   <li>service-requests-done: 等待现有请求完成</li>
     *   <li>service-stop: 停止业务服务（Bootstrap 清理）</li>
     *   <li>cluster-sharding-shutdown-region: 关闭 Sharding Region</li>
     *   <li>cluster-leave: 离开集群</li>
     *   <li>cluster-exiting: 集群退出中</li>
     *   <li>cluster-exiting-done: 集群退出完成</li>
     *   <li>cluster-shutdown: 集群关闭</li>
     *   <li>before-actor-system-terminate: ActorSystem 终止前</li>
     *   <li>actor-system-terminate: 终止 ActorSystem</li>
     * </ul>
     * 
     * @param processType 进程类型（用于日志）
     */
    private void registerCoordinatedShutdown(ProcessType processType) {
        CoordinatedShutdown shutdown = CoordinatedShutdown.get(system);
        
        // 在 service-stop 阶段关闭所有 Bootstrap 组件
        // 该阶段在集群 sharding 关闭之前,在服务停止接受新请求之后
        shutdown.addTask(
            CoordinatedShutdown.PhaseServiceStop(),
            "shutdown-bootstraps",
            () -> {
                logger.info("[CoordinatedShutdown] Shutting down {} bootstraps...", processType);
                try {
                    if (bootstrapManager != null) {
                        bootstrapManager.shutdownAll();
                    }
                    logger.info("[CoordinatedShutdown] Bootstraps shutdown completed");
                } catch (Exception e) {
                    logger.error("[CoordinatedShutdown] Error shutting down bootstraps", e);
                }
                return CompletableFuture.completedFuture(null);
            }
        );
        
        logger.info("CoordinatedShutdown tasks registered for {}", processType);
    }

    private int parseInstanceId(String[] args) {
        if (args.length > 0) {
            try {
                return Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                logger.warn("Invalid instance ID '{}', defaulting to 0", args[0]);
            }
        }
        return 0;
    }

    /**
     * 定义进程类型（子类必须实现）
     */
    protected abstract ProcessType getProcessType();
    
    /**
     * 注册 Bootstrap 组件（子类可选实现）
     * 
     * <p>子类通过此方法注册需要的 Bootstrap 组件。
     * 
     * <h2>示例</h2>
     * <pre>
     * &#64;Override
     * protected void registerBootstraps(BootstrapManager manager) {
     *     manager.register(new ChannelBootstrap());
     *     manager.register(new NettyBootstrap());
     * }
     * </pre>
     * 
     * @param manager Bootstrap 管理器
     */
    protected void registerBootstraps(BootstrapManager manager) {
        // 默认不注册任何 Bootstrap，子类覆盖此方法
    }

    /**
     * Whether this node should join the cluster via default seed nodes.
     * Default is true. Gateway-0 might override this.
     */
    protected boolean shouldJoinCluster() {
        return true;
    }
}
