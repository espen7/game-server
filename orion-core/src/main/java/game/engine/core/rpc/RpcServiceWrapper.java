package game.engine.core.rpc;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.pattern.Patterns;
import org.apache.pekko.util.Timeout;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * RPC服务包装器
 * 将Actor包装成服务提供者接口
 */
public class RpcServiceWrapper implements ServiceProvider {
    private final ActorRef serviceActor;
    private final String serviceName;

    public RpcServiceWrapper(ActorRef serviceActor, String serviceName) {
        this.serviceActor = serviceActor;
        this.serviceName = serviceName;
    }

    @Override
    public String getServiceName() {
        return serviceName;
    }

    @Override
    public Object invokeMethod(String methodName, Object... parameters) throws RpcException {
        try {
            // 创建服务调用消息
            ServiceInvokeMessage invokeMsg = new ServiceInvokeMessage(methodName, parameters);
            
            // 设置超时时间
            Timeout timeout = Timeout.apply(5, TimeUnit.SECONDS);
            Future<Object> future = Patterns.ask(serviceActor, invokeMsg, timeout);
            
            // 等待结果
            Object result = Await.result(future, Duration.apply(5, TimeUnit.SECONDS));
            
            if (result instanceof ServiceResultMessage) {
                ServiceResultMessage resultMsg = (ServiceResultMessage) result;
                if (resultMsg.isSuccess()) {
                    return resultMsg.getResult();
                } else {
                    throw new RpcException(RpcException.RpcErrorType.INTERNAL_ERROR,
                                         resultMsg.getError(), serviceName);
                }
            } else {
                throw new RpcException(RpcException.RpcErrorType.INTERNAL_ERROR,
                                     "Unexpected response type: " + result.getClass(), serviceName);
            }
        } catch (Exception e) {
            throw new RpcException(RpcException.RpcErrorType.INTERNAL_ERROR,
                                 "Service invocation failed: " + e.getMessage(), serviceName, e);
        }
    }

    /**
     * 异步调用服务方法
     */
    public CompletableFuture<Object> invokeMethodAsync(String methodName, Object... parameters) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return invokeMethod(methodName, parameters);
            } catch (RpcException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public boolean isAvailable() {
        // 简单检查Actor是否存活
        return serviceActor != null && !serviceActor.isTerminated();
    }

    @Override
    public String getHealthStatus() {
        if (isAvailable()) {
            return "healthy";
        } else {
            return "unavailable";
        }
    }

    /**
     * 服务调用消息
     */
    public static class ServiceInvokeMessage {
        private final String methodName;
        private final Object[] parameters;

        public ServiceInvokeMessage(String methodName, Object[] parameters) {
            this.methodName = methodName;
            this.parameters = parameters;
        }

        public String getMethodName() { return methodName; }
        public Object[] getParameters() { return parameters; }
    }

    /**
     * 服务结果消息
     */
    public static class ServiceResultMessage {
        private final Object result;
        private final String error;
        private final boolean success;

        public ServiceResultMessage(Object result) {
            this.result = result;
            this.error = null;
            this.success = true;
        }

        public ServiceResultMessage(String error) {
            this.result = null;
            this.error = error;
            this.success = false;
        }

        public Object getResult() { return result; }
        public String getError() { return error; }
        public boolean isSuccess() { return success; }
    }
}