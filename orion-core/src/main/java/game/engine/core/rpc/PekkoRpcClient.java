package game.engine.core.rpc;

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
 * 基于Pekko的服务调用者实现
 * 利用Pekko的Actor系统进行远程过程调用
 */
public class PekkoRpcClient implements ServiceInvoker {
    private final ActorSystem actorSystem;
    private final ActorRef rpcServiceRegistry;

    public PekkoRpcClient(ActorSystem actorSystem) {
        this.actorSystem = actorSystem;
        // 获取RPC服务注册中心的引用
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
        RpcRequest request = new RpcRequest(serviceName, methodName, parameters);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                Timeout timeout = Timeout.apply(request.getTimeoutMs(), TimeUnit.MILLISECONDS);
                Future<Object> future = Patterns.ask(rpcServiceRegistry, request, timeout);
                
                // 等待响应
                Object result = Await.result(future, Duration.apply(request.getTimeoutMs(), TimeUnit.MILLISECONDS));
                
                if (result instanceof RpcResponse) {
                    return (RpcResponse) result;
                } else {
                    // 如果返回的是异常或其他类型，包装成RpcResponse
                    return new RpcResponse(request.getRequestId(), 
                                         new RpcException(RpcException.RpcErrorType.INTERNAL_ERROR, 
                                                        "Unexpected response type: " + result.getClass()));
                }
            } catch (Exception e) {
                return new RpcResponse(request.getRequestId(),
                                     new RpcException(RpcException.RpcErrorType.TIMEOUT, 
                                                    "Call timeout or failed: " + e.getMessage(), 
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
        RpcRequest request = new RpcRequest(serviceName, methodName, parameters, timeoutMs, 1);
        
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
                                 "Call failed: " + e.getMessage(), request.getRequestId(), e);
        }
    }

    @Override
    public void close() {
        // Pekko调用者不需要显式关闭，由ActorSystem管理
    }
}