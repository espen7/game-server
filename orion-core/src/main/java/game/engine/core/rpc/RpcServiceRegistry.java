package game.engine.core.rpc;

import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import org.apache.pekko.event.Logging;
import org.apache.pekko.event.LoggingAdapter;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * RPC服务注册中心
 * 管理所有RPC服务的注册和发现
 */
public class RpcServiceRegistry extends AbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    private final Map<String, ActorRef> serviceActors = new HashMap<>();
    private final Map<String, RpcServiceWrapper> services = new HashMap<>();

    public static Props props() {
        return Props.create(RpcServiceRegistry.class, RpcServiceRegistry::new);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(RegisterService.class, this::handleRegisterService)
                .match(UnregisterService.class, this::handleUnregisterService)
                .match(RpcRequest.class, this::handleRpcRequest)
                .match(GetServiceList.class, this::handleGetServiceList)
                .matchAny(this::handleUnknown)
                .build();
    }

    private void handleRegisterService(RegisterService msg) {
        log.info("Registering service: {} with actor: {}", msg.serviceName, msg.serviceActor.path());
        serviceActors.put(msg.serviceName, msg.serviceActor);
        
        // 尝试获取服务包装器
        if (msg.serviceActor != null) {
            services.put(msg.serviceName, new RpcServiceWrapper(msg.serviceActor, msg.serviceName));
        }
        
        sender().tell(new ServiceRegistered(msg.serviceName), self());
    }

    private void handleUnregisterService(UnregisterService msg) {
        log.info("Unregistering service: {}", msg.serviceName);
        serviceActors.remove(msg.serviceName);
        services.remove(msg.serviceName);
        sender().tell(new ServiceUnregistered(msg.serviceName), self());
    }

    private void handleRpcRequest(RpcRequest request) {
        String serviceName = request.getServiceName();
        
        // 记录调用来源类型
        String callType = request.getCallContext().getCallType().toString();
        String callerId = request.getCallContext().getCallerId();
        log.info("RPC call received - Type: {}, Caller: {}, Target: {}, Method: {}", 
                callType, callerId, serviceName, request.getMethodName());
        
        if (!services.containsKey(serviceName)) {
            log.warning("Service not found: {} (called by {})", serviceName, callerId);
            sender().tell(
                new RpcResponse(request.getRequestId(),
                              new RpcException(RpcException.RpcErrorType.SERVICE_NOT_FOUND,
                                             "Service not found: " + serviceName,
                                             request.getRequestId())),
                self()
            );
            return;
        }

        RpcServiceWrapper service = services.get(serviceName);
        try {
            // 根据调用类型应用不同的处理策略
            if (request.isClientCall()) {
                handleClientCall(request, service);
            } else if (request.isInternalCall()) {
                handleInternalCall(request, service);
            } else {
                handleSystemCall(request, service);
            }
            
        } catch (Exception e) {
            log.error(e, "Failed to invoke service method: {}.{} (caller: {})",
                     serviceName, request.getMethodName(), callerId);
            sender().tell(
                new RpcResponse(request.getRequestId(),
                              new RpcException(RpcException.RpcErrorType.INTERNAL_ERROR,
                                             "Service invocation error: " + e.getMessage(),
                                             request.getRequestId(), e)),
                self()
            );
        }
    }
    
    /**
     * 处理客户端调用
     */
    private void handleClientCall(RpcRequest request, RpcServiceWrapper service) {
        // 对客户端调用应用额外的安全检查和限制
        if (!validateClientCall(request)) {
            sender().tell(
                new RpcResponse(request.getRequestId(),
                              new RpcException(RpcException.RpcErrorType.SERVICE_UNAVAILABLE,
                                             "Client call validation failed",
                                             request.getRequestId())),
                self()
            );
            return;
        }
        
        invokeServiceMethod(request, service);
    }
    
    /**
     * 处理内部服务调用
     */
    private void handleInternalCall(RpcRequest request, RpcServiceWrapper service) {
        // 对内部服务调用进行认证
        if (!validateInternalCall(request)) {
            sender().tell(
                new RpcResponse(request.getRequestId(),
                              new RpcException(RpcException.RpcErrorType.SERVICE_UNAVAILABLE,
                                             "Internal service authentication failed",
                                             request.getRequestId())),
                self()
            );
            return;
        }
        
        invokeServiceMethod(request, service);
    }
    
    /**
     * 处理系统调用
     */
    private void handleSystemCall(RpcRequest request, RpcServiceWrapper service) {
        // 系统调用通常有最高权限
        invokeServiceMethod(request, service);
    }
    
    /**
     * 验证客户端调用
     */
    private boolean validateClientCall(RpcRequest request) {
        // 实现客户端调用的具体验证逻辑
        // 如：权限检查、参数验证、速率限制等
        return true; // 简化实现
    }
    
    /**
     * 验证内部服务调用
     */
    private boolean validateInternalCall(RpcRequest request) {
        // 实现服务间调用的认证逻辑
        // 如：服务证书验证、调用链验证等
        return true; // 简化实现
    }
    
    /**
     * 调用服务方法
     */
    private void invokeServiceMethod(RpcRequest request, RpcServiceWrapper service) {
        CompletableFuture<Object> future = service.invokeMethodAsync(request.getMethodName(), request.getParameters());
        
        future.whenComplete((result, throwable) -> {
            if (throwable != null) {
                log.error("Service invocation failed: {} (caller: {})", 
                         throwable.getMessage(), request.getCallContext().getCallerId());
                sender().tell(
                    new RpcResponse(request.getRequestId(),
                                  new RpcException(RpcException.RpcErrorType.INTERNAL_ERROR,
                                                 "Service invocation failed: " + throwable.getMessage(),
                                                 request.getRequestId(), throwable)),
                    self()
                );
            } else {
                sender().tell(new RpcResponse(request.getRequestId(), result), self());
            }
        });
    }

    private void handleGetServiceList(GetServiceList msg) {
        sender().tell(new ServiceList(serviceActors.keySet()), self());
    }

    private void handleUnknown(Object msg) {
        log.warning("Received unknown message: {}", msg.getClass().getSimpleName());
    }

    // 消息定义
    public static class RegisterService {
        public final String serviceName;
        public final ActorRef serviceActor;

        public RegisterService(String serviceName, ActorRef serviceActor) {
            this.serviceName = serviceName;
            this.serviceActor = serviceActor;
        }
    }

    public static class UnregisterService {
        public final String serviceName;

        public UnregisterService(String serviceName) {
            this.serviceName = serviceName;
        }
    }

    public static class GetServiceList {}

    public static class ServiceRegistered {
        public final String serviceName;

        public ServiceRegistered(String serviceName) {
            this.serviceName = serviceName;
        }
    }

    public static class ServiceUnregistered {
        public final String serviceName;

        public ServiceUnregistered(String serviceName) {
            this.serviceName = serviceName;
        }
    }

    public static class ServiceList {
        public final java.util.Set<String> serviceNames;

        public ServiceList(java.util.Set<String> serviceNames) {
            this.serviceNames = serviceNames;
        }
    }
}