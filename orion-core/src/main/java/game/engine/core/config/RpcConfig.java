package game.engine.core.config;

import game.engine.core.rpc.RpcServiceRegistry;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RPC系统配置类
 * 负责初始化RPC服务注册中心和其他RPC相关组件
 */
public class RpcConfig {
    private static final Logger logger = LoggerFactory.getLogger(RpcConfig.class);
    
    public static final String RPC_SERVICE_REGISTRY_NAME = "rpc-service-registry";
    public static final String RPC_SERVICE_REGISTRY_PATH = "/user/" + RPC_SERVICE_REGISTRY_NAME;

    /**
     * 初始化RPC系统
     * @param actorSystem Actor系统
     */
    public static void initializeRpcSystem(ActorSystem actorSystem) {
        logger.info("Initializing RPC system...");
        
        try {
            // 启动RPC服务注册中心
            actorSystem.actorOf(RpcServiceRegistry.props(), RPC_SERVICE_REGISTRY_NAME);
            logger.info("RPC service registry started at: {}", RPC_SERVICE_REGISTRY_PATH);
            
        } catch (Exception e) {
            logger.error("Failed to initialize RPC system", e);
            throw new RuntimeException("RPC system initialization failed", e);
        }
    }

    /**
     * 注册RPC服务
     * @param actorSystem Actor系统
     * @param serviceName 服务名称
     * @param serviceActor 服务Actor引用
     */
    public static void registerRpcService(ActorSystem actorSystem, String serviceName, org.apache.pekko.actor.ActorRef serviceActor) {
        try {
            org.apache.pekko.actor.ActorRef registry = actorSystem.actorSelection(RPC_SERVICE_REGISTRY_PATH)
                    .resolveOneCS(scala.concurrent.duration.Duration.create(3, java.util.concurrent.TimeUnit.SECONDS))
                    .toCompletableFuture().join();
            
            registry.tell(new RpcServiceRegistry.RegisterService(serviceName, serviceActor), 
                         org.apache.pekko.actor.ActorRef.noSender());
            
            logger.info("RPC service registered: {} -> {}", serviceName, serviceActor.path());
            
        } catch (Exception e) {
            logger.error("Failed to register RPC service: {}", serviceName, e);
        }
    }

    /**
     * 注销RPC服务
     * @param actorSystem Actor系统
     * @param serviceName 服务名称
     */
    public static void unregisterRpcService(ActorSystem actorSystem, String serviceName) {
        try {
            org.apache.pekko.actor.ActorRef registry = actorSystem.actorSelection(RPC_SERVICE_REGISTRY_PATH)
                    .resolveOneCS(scala.concurrent.duration.Duration.create(3, java.util.concurrent.TimeUnit.SECONDS))
                    .toCompletableFuture().join();
            
            registry.tell(new RpcServiceRegistry.UnregisterService(serviceName), 
                         org.apache.pekko.actor.ActorRef.noSender());
            
            logger.info("RPC service unregistered: {}", serviceName);
            
        } catch (Exception e) {
            logger.error("Failed to unregister RPC service: {}", serviceName, e);
        }
    }
}