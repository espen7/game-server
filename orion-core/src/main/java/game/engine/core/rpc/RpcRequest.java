package game.engine.core.rpc;

import game.engine.core.rpc.context.RpcCallContext;

import java.io.Serializable;
import java.util.UUID;

/**
 * RPC请求封装类
 * 包含请求元数据和调用信息
 */
public class RpcRequest implements Serializable {
    private final String requestId;
    private final String serviceName;
    private final String methodName;
    private final Object[] parameters;
    private final long timestamp;
    private final long timeoutMs;
    private final int retryCount;
    private final RpcCallContext callContext;

    public RpcRequest(String serviceName, String methodName, Object[] parameters) {
        this(serviceName, methodName, parameters, RpcConstants.DEFAULT_TIMEOUT_MS, RpcConstants.DEFAULT_RETRIES, null);
    }

    public RpcRequest(String serviceName, String methodName, Object[] parameters,
            long timeoutMs, int retryCount) {
        this(serviceName, methodName, parameters, timeoutMs, retryCount, null);
    }

    public RpcRequest(String serviceName, String methodName, Object[] parameters,
            long timeoutMs, int retryCount, RpcCallContext callContext) {
        this.requestId = UUID.randomUUID().toString();
        this.serviceName = serviceName;
        this.methodName = methodName;
        this.parameters = parameters;
        this.timestamp = System.currentTimeMillis();
        this.timeoutMs = timeoutMs;
        this.retryCount = retryCount;
        this.callContext = callContext != null ? callContext : RpcCallContext.newInternalContext("unknown-service");
    }

    // Getters
    public String getRequestId() {
        return requestId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getMethodName() {
        return methodName;
    }

    public Object[] getParameters() {
        return parameters;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public RpcCallContext getCallContext() {
        return callContext;
    }

    /**
     * 是否为客户端调用
     */
    public boolean isClientCall() {
        return callContext != null && callContext.isClientCall();
    }

    /**
     * 是否为内部服务调用
     */
    public boolean isInternalCall() {
        return callContext != null && callContext.isInternalCall();
    }

    /**
     * 是否为系统调用
     */
    public boolean isSystemCall() {
        return callContext != null && callContext.isSystemCall();
    }

    @Override
    public String toString() {
        return String.format("RpcRequest{id=%s, service=%s, method=%s}",
                requestId, serviceName, methodName);
    }
}