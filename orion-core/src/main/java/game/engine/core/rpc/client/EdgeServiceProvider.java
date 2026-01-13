package game.engine.core.rpc.client;

import game.engine.core.rpc.*;
import game.engine.core.rpc.context.RpcCallContext;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.pattern.Patterns;
import org.apache.pekko.util.Timeout;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 边缘服务提供者
 * 作为外部访问的统一入口，提供完整的API网关功能
 * 实施边缘安全防护、流量控制和协议转换
 */
public class EdgeServiceProvider implements ServiceInvoker {
    private final ActorSystem actorSystem;
    private final ActorRef rpcServiceRegistry;
    private final String providerId;
    private final EdgeSecurityManager securityManager;

    public EdgeServiceProvider(ActorSystem actorSystem, String providerId) {
        this.actorSystem = actorSystem;
        this.providerId = providerId;
        this.securityManager = new EdgeSecurityManager();
        
        try {
            this.rpcServiceRegistry = actorSystem.actorSelection("/user/rpc-service-registry")
                    .resolveOneCS(Duration.create(3, TimeUnit.SECONDS))
                    .toCompletableFuture().join();
        } catch (Exception e) {
            throw new RuntimeException("Failed to connect to RPC service registry", e);
        }
    }

    @Override
    public CompletableFuture<RpcResponse> callAsync(String serviceName, String methodName, Object... parameters) {
        // 安全检查
        if (!securityManager.isAllowed(providerId, serviceName, methodName)) {
            return CompletableFuture.completedFuture(
                new RpcResponse("denied-" + System.currentTimeMillis(),
                              new RpcException(RpcException.RpcErrorType.SERVICE_UNAVAILABLE,
                                             "Access denied for provider: " + providerId)));
        }

        // 创建客户端调用上下文
        RpcCallContext context = RpcCallContext.newClientContext(providerId);
        RpcRequest request = new RpcRequest(serviceName, methodName, parameters, 5000, 3, context);
        
        // 速率限制检查
        if (!securityManager.checkRateLimit(providerId, serviceName)) {
            return CompletableFuture.completedFuture(
                new RpcResponse(request.getRequestId(),
                              new RpcException(RpcException.RpcErrorType.SERVICE_UNAVAILABLE,
                                             "Rate limit exceeded for provider: " + providerId)));
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                Timeout timeout = Timeout.apply(request.getTimeoutMs(), TimeUnit.MILLISECONDS);
                Future<Object> future = Patterns.ask(rpcServiceRegistry, request, timeout);
                
                Object result = Await.result(future, Duration.apply(request.getTimeoutMs(), TimeUnit.MILLISECONDS));
                
                if (result instanceof RpcResponse) {
                    return (RpcResponse) result;
                } else {
                    return new RpcResponse(request.getRequestId(),
                                         new RpcException(RpcException.RpcErrorType.INTERNAL_ERROR,
                                                        "Unexpected response type"));
                }
            } catch (Exception e) {
                return new RpcResponse(request.getRequestId(),
                                     new RpcException(RpcException.RpcErrorType.TIMEOUT,
                                                    "Client call timeout or failed: " + e.getMessage(),
                                                    request.getRequestId(), e));
            }
        });
    }

    @Override
    public RpcResponse callSync(String serviceName, String methodName, Object... parameters) throws RpcException {
        return callSyncWithTimeout(serviceName, methodName, 5000, parameters);
    }

    @Override
    public RpcResponse callSyncWithTimeout(String serviceName, String methodName, long timeoutMs, Object... parameters) throws RpcException {
        // 安全检查
        if (!securityManager.isAllowed(providerId, serviceName, methodName)) {
            throw new RpcException(RpcException.RpcErrorType.SERVICE_UNAVAILABLE,
                                 "Access denied for provider: " + providerId);
        }

        // 速率限制检查
        if (!securityManager.checkRateLimit(providerId, serviceName)) {
            throw new RpcException(RpcException.RpcErrorType.SERVICE_UNAVAILABLE,
                                 "Rate limit exceeded for provider: " + providerId);
        }

        RpcCallContext context = RpcCallContext.newClientContext(providerId);
        RpcRequest request = new RpcRequest(serviceName, methodName, parameters, timeoutMs, 1, context);
        
        try {
            Timeout timeout = Timeout.apply(timeoutMs, TimeUnit.MILLISECONDS);
            Future<Object> future = Patterns.ask(rpcServiceRegistry, request, timeout);
            
            Object result = Await.result(future, Duration.apply(timeoutMs, TimeUnit.MILLISECONDS));
            
            if (result instanceof RpcResponse) {
                RpcResponse response = (RpcResponse) result;
                if (!response.isSuccess()) {
                    throw response.getException();
                }
                return response;
            } else {
                throw new RpcException(RpcException.RpcErrorType.INTERNAL_ERROR,
                                     "Unexpected response type: " + result.getClass());
            }
        } catch (RpcException e) {
            throw e;
        } catch (Exception e) {
            throw new RpcException(RpcException.RpcErrorType.TIMEOUT,
                                 "Client call failed: " + e.getMessage(), request.getRequestId(), e);
        }
    }

    @Override
    public void close() {
        // 清理服务提供者资源
        securityManager.cleanupProvider(providerId);
    }

    /**
     * 边缘安全管理器
     * 负责权限验证、速率限制等安全相关功能
     */
    private static class EdgeSecurityManager {
        // 简化的权限控制 - 实际项目中应该从配置或数据库加载
        private static final String[] ALLOWED_SERVICES = {"player-service", "world-service"};
        private static final int MAX_CALLS_PER_MINUTE = 100;
        
        /**
         * 检查服务提供者是否有权限调用指定服务和方法
         */
        public boolean isAllowed(String providerId, String serviceName, String methodName) {
            // 基本的服务白名单检查
            for (String allowedService : ALLOWED_SERVICES) {
                if (allowedService.equals(serviceName)) {
                    return true;
                }
            }
            return false;
        }
        
        /**
         * 检查速率限制
         */
        public boolean checkRateLimit(String providerId, String serviceName) {
            // 简化的速率限制实现
            // 实际项目中应该使用Redis或其他分布式缓存来跟踪调用频率
            return true; // 暂时允许所有调用
        }
        
        /**
         * 清理服务提供者相关资源
         */
        public void cleanupProvider(String providerId) {
            // 清理该服务提供者的统计信息和临时数据
        }
    }
}