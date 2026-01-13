package game.engine.core.rpc.system;

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
 * 核心服务编排器
 * 专门处理系统级任务和基础设施服务调用
 * 具有最高权限，适用于定时任务、监控、运维等场景
 */
public class CoreServiceOrchestrator implements ServiceInvoker {
    private final ActorSystem actorSystem;
    private final ActorRef rpcServiceRegistry;
    private final String orchestratorId;
    private final SystemSecurityManager securityManager;

    public CoreServiceOrchestrator(ActorSystem actorSystem, String orchestratorId) {
        this.actorSystem = actorSystem;
        this.orchestratorId = orchestratorId;
        this.securityManager = new SystemSecurityManager();
        
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
        // 系统级权限验证
        if (!securityManager.hasSystemPrivilege(orchestratorId, serviceName, methodName)) {
            return CompletableFuture.completedFuture(
                new RpcResponse("denied-" + System.currentTimeMillis(),
                              new RpcException(RpcException.RpcErrorType.SERVICE_UNAVAILABLE,
                                             "Insufficient system privileges: " + orchestratorId)));
        }

        // 创建系统调用上下文
        RpcCallContext context = RpcCallContext.newSystemContext(orchestratorId);
        RpcRequest request = new RpcRequest(serviceName, methodName, parameters, 10000, 0, context);
        
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
                                                        "Unexpected infrastructure response type"));
                }
            } catch (Exception e) {
                return new RpcResponse(request.getRequestId(),
                                     new RpcException(RpcException.RpcErrorType.TIMEOUT,
                                                    "Infrastructure call timeout: " + e.getMessage(),
                                                    request.getRequestId(), e));
            }
        });
    }

    @Override
    public RpcResponse callSync(String serviceName, String methodName, Object... parameters) throws RpcException {
        return callSyncWithTimeout(serviceName, methodName, 10000, parameters);
    }

    @Override
    public RpcResponse callSyncWithTimeout(String serviceName, String methodName, long timeoutMs, Object... parameters) throws RpcException {
        // 系统级权限验证
        if (!securityManager.hasSystemPrivilege(orchestratorId, serviceName, methodName)) {
            throw new RpcException(RpcException.RpcErrorType.SERVICE_UNAVAILABLE,
                                 "Insufficient system privileges: " + orchestratorId);
        }

        RpcCallContext context = RpcCallContext.newSystemContext(orchestratorId);
        RpcRequest request = new RpcRequest(serviceName, methodName, parameters, timeoutMs, 0, context);
        
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
                                 "Infrastructure call failed: " + e.getMessage(), request.getRequestId(), e);
        }
    }

    @Override
    public void close() {
        // 核心服务编排器资源清理
    }

    /**
     * 系统安全管理器
     * 负责系统级组件的权限管理和安全控制
     */
    private static class SystemSecurityManager {
        // 系统组件权限映射
        private static final String[][] SYSTEM_PRIVILEGES = {
            {"scheduler-service", "player-service", "*"},      // 调度服务可以调用Player服务的所有方法
            {"scheduler-service", "world-service", "*"},       // 调度服务可以调用World服务的所有方法
            {"monitor-service", "player-service", "get*"},     // 监控服务只能调用Player服务的查询方法
            {"monitor-service", "world-service", "get*"},      // 监控服务只能调用World服务的查询方法
            {"admin-tool", "*", "*"}                           // 管理工具具有完全权限
        };
        
        /**
         * 检查系统编排器是否具有指定权限
         */
        public boolean hasSystemPrivilege(String orchestratorId, String serviceName, String methodName) {
            for (String[] privilege : SYSTEM_PRIVILEGES) {
                if (matchesPattern(privilege[0], orchestratorId) &&
                    matchesPattern(privilege[1], serviceName) &&
                    (privilege[2].equals("*") || matchesPattern(privilege[2], methodName))) {
                    return true;
                }
            }
            return false;
        }
        
        /**
         * 简单的模式匹配
         */
        private boolean matchesPattern(String pattern, String value) {
            if (pattern.equals("*")) {
                return true;
            }
            if (pattern.endsWith("*")) {
                return value.startsWith(pattern.substring(0, pattern.length() - 1));
            }
            return pattern.equals(value);
        }
    }
}