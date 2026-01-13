package game.engine.core.rpc;

/**
 * RPC异常基类
 */
public class RpcException extends Exception {
    private final RpcErrorType errorType;
    private final String requestId;

    public RpcException(RpcErrorType errorType, String message) {
        this(errorType, message, null, null);
    }

    public RpcException(RpcErrorType errorType, String message, String requestId) {
        this(errorType, message, requestId, null);
    }

    public RpcException(RpcErrorType errorType, String message, String requestId, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
        this.requestId = requestId;
    }

    public RpcErrorType getErrorType() { return errorType; }
    public String getRequestId() { return requestId; }

    @Override
    public String toString() {
        return String.format("RpcException[type=%s, requestId=%s, message=%s]", 
                           errorType, requestId, getMessage());
    }

    /**
     * RPC错误类型枚举
     */
    public enum RpcErrorType {
        TIMEOUT("请求超时"),
        SERVICE_NOT_FOUND("服务未找到"),
        METHOD_NOT_FOUND("方法未找到"),
        INVALID_PARAMETERS("参数无效"),
        SERVICE_UNAVAILABLE("服务不可用"),
        INTERNAL_ERROR("内部错误"),
        NETWORK_ERROR("网络错误");

        private final String description;

        RpcErrorType(String description) {
            this.description = description;
        }

        public String getDescription() { return description; }
    }
}