package game.engine.core.rpc;

import java.io.Serializable;

/**
 * RPC响应封装类
 * 包含响应结果或异常信息
 */
public class RpcResponse implements Serializable {
    private final String requestId;
    private final Object result;
    private final RpcException exception;
    private final long timestamp;

    public RpcResponse(String requestId, Object result) {
        this(requestId, result, null);
    }

    public RpcResponse(String requestId, RpcException exception) {
        this(requestId, null, exception);
    }

    private RpcResponse(String requestId, Object result, RpcException exception) {
        this.requestId = requestId;
        this.result = result;
        this.exception = exception;
        this.timestamp = System.currentTimeMillis();
    }

    public boolean isSuccess() {
        return exception == null;
    }

    public String getRequestId() { return requestId; }
    public Object getResult() { return result; }
    public RpcException getException() { return exception; }
    public long getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        if (isSuccess()) {
            return String.format("RpcResponse{id=%s, result=%s}", requestId, result);
        } else {
            return String.format("RpcResponse{id=%s, error=%s}", requestId, exception.getMessage());
        }
    }

    /**
     * 获取结果，如果失败则抛出异常
     */
    @SuppressWarnings("unchecked")
    public <T> T getResultOrThrow() throws RpcException {
        if (exception != null) {
            throw exception;
        }
        return (T) result;
    }
}