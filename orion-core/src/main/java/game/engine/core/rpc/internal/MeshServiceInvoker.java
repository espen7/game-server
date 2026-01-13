package game.engine.core.rpc.internal;

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
 * 网格服务调用者
 * 专门处理微服务网格内的服务间调用
 * 实施服务网格安全策略和流量治理
 */
public class MeshServiceInvoker implements ServiceInvoker {
    private final ActorSystem actorSystem;
    private final ActorRef rpcServiceRegistry;
    private final String serviceName;
    private final ServiceAuthenticator authenticator;

    public MeshServiceInvoker(ActorSystem actorSystem, String serviceName) {
        this.actorSystem = actorSystem;
        this.serviceName = serviceName;
        this.authenticator = new ServiceAuthenticator();
        
        try {
            this.rpcServiceRegistry = actorSystem.actorSelection("/user/rpc-service-registry")
                    .resolveOneCS(Duration.create(3, TimeUnit.SECONDS))
                    .toCompletableFuture().join();
        } catch (Exception e) {
            throw new RuntimeException("Failed to connect to RPC service registry", e);
        }
    }

    @Override
    public CompletableFuture<RpcResponse> callAsync(String targetService, String methodName, Object... parameters) {
        // 服务间认证
        if (!authenticator.authenticateService(serviceName, targetService)) {
            return CompletableFuture.completedFuture(
                new RpcResponse("denied-" + System.currentTimeMillis(),
                              new RpcException(RpcException.RpcErrorType.SERVICE_UNAVAILABLE,
                                             "Service authentication failed: " + serviceName + " -> " + targetService)));
        }

        // 创建内部服务调用上下文
        RpcCallContext context = RpcCallContext.newInternalContext(serviceName);
        RpcRequest request = new RpcRequest(targetService, methodName, parameters, 3000, 2, context);
        
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
                                                        "Unexpected response type from internal service"));
                }
            } catch (Exception e) {
                return new RpcResponse(request.getRequestId(),
                                     new RpcException(RpcException.RpcErrorType.TIMEOUT,
                                                    "Internal service call timeout: " + e.getMessage(),
                                                    request.getRequestId(), e));
            }
        });
    }

    @Override
    public RpcResponse callSync(String targetService, String methodName, Object... parameters) throws RpcException {
        return callSyncWithTimeout(targetService, methodName, 3000, parameters);
    }

    @Override
    public RpcResponse callSyncWithTimeout(String targetService, String methodName, long timeoutMs, Object... parameters) throws RpcException {
        // 服务间认证
        if (!authenticator.authenticateService(serviceName, targetService)) {
            throw new RpcException(RpcException.RpcErrorType.SERVICE_UNAVAILABLE,
                                 "Service authentication failed: " + serviceName + " -> " + targetService);
        }

        RpcCallContext context = RpcCallContext.newInternalContext(serviceName);
        RpcRequest request = new RpcRequest(targetService, methodName, parameters, timeoutMs, 1, context);
        
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
                                 "Internal service call failed: " + e.getMessage(), request.getRequestId(), e);
        }
    }

    @Override
    public void close() {
        // 网格服务调用者清理
    }

    /**
     * 服务间认证器
     * 负责验证服务间的调用权限
     */
    private static class ServiceAuthenticator {
        // 简化的服务认证 - 实际项目中应该使用证书、token等方式
        private static final String[][] ALLOWED_SERVICE_CALLS = {
            {"gateway-service", "player-service"},
            {"gateway-service", "world-service"},
            {"player-service", "world-service"},
            {"world-service", "player-service"}
        };
        
        /**
         * 验证服务间调用权限
         */
        public boolean authenticateService(String callerService, String targetService) {
            // 检查是否在允许的服务调用列表中
            for (String[] allowedCall : ALLOWED_SERVICE_CALLS) {
                if (allowedCall[0].equals(callerService) && allowedCall[1].equals(targetService)) {
                    return true;
                }
            }
            
            // 相同服务内的调用总是允许的
            if (callerService.equals(targetService)) {
                return true;
            }
            
            return false;
        }
    }
}