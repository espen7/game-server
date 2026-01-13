package game.engine.core.rpc.context;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * RPC调用上下文
 * 包含调用的元数据信息，用于区分调用类型和来源
 */
public class RpcCallContext implements Serializable {
    private final String traceId;           // 调用链追踪ID
    private final String callerId;          // 调用方标识
    private final CallType callType;        // 调用类型
    private final long timestamp;           // 调用时间戳
    private final Map<String, Object> attributes; // 自定义属性
    
    private RpcCallContext(Builder builder) {
        this.traceId = builder.traceId != null ? builder.traceId : UUID.randomUUID().toString();
        this.callerId = builder.callerId;
        this.callType = builder.callType != null ? builder.callType : CallType.INTERNAL;
        this.timestamp = builder.timestamp != 0 ? builder.timestamp : System.currentTimeMillis();
        this.attributes = builder.attributes != null ? new HashMap<>(builder.attributes) : new HashMap<>();
    }
    
    public static Builder newBuilder() {
        return new Builder();
    }
    
    // Getters
    public String getTraceId() { return traceId; }
    public String getCallerId() { return callerId; }
    public CallType getCallType() { return callType; }
    public long getTimestamp() { return timestamp; }
    public Map<String, Object> getAttributes() { return new HashMap<>(attributes); }
    
    public Object getAttribute(String key) {
        return attributes.get(key);
    }
    
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }
    
    /**
     * 是否为客户端调用
     */
    public boolean isClientCall() {
        return callType == CallType.CLIENT;
    }
    
    /**
     * 是否为内部服务调用
     */
    public boolean isInternalCall() {
        return callType == CallType.INTERNAL;
    }
    
    /**
     * 是否为系统调用（如定时任务、后台作业）
     */
    public boolean isSystemCall() {
        return callType == CallType.SYSTEM;
    }
    
    @Override
    public String toString() {
        return String.format("RpcCallContext{traceId='%s', callerId='%s', callType=%s}", 
                           traceId, callerId, callType);
    }
    
    /**
     * 调用类型枚举
     */
    public enum CallType {
        CLIENT("客户端调用"),      // 来自外部客户端的调用
        INTERNAL("内部服务调用"),   // 服务间相互调用
        SYSTEM("系统调用");       // 系统内部调用（定时任务等）
        
        private final String description;
        
        CallType(String description) {
            this.description = description;
        }
        
        public String getDescription() { return description; }
    }
    
    /**
     * Builder模式构建器
     */
    public static class Builder {
        private String traceId;
        private String callerId;
        private CallType callType;
        private long timestamp;
        private Map<String, Object> attributes;
        
        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }
        
        public Builder callerId(String callerId) {
            this.callerId = callerId;
            return this;
        }
        
        public Builder callType(CallType callType) {
            this.callType = callType;
            return this;
        }
        
        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public Builder attribute(String key, Object value) {
            if (this.attributes == null) {
                this.attributes = new HashMap<>();
            }
            this.attributes.put(key, value);
            return this;
        }
        
        public RpcCallContext build() {
            return new RpcCallContext(this);
        }
    }
    
    /**
     * 创建客户端调用上下文
     */
    public static RpcCallContext newClientContext(String clientId) {
        return newBuilder()
                .callerId(clientId)
                .callType(CallType.CLIENT)
                .build();
    }
    
    /**
     * 创建内部服务调用上下文
     */
    public static RpcCallContext newInternalContext(String serviceName) {
        return newBuilder()
                .callerId(serviceName)
                .callType(CallType.INTERNAL)
                .build();
    }
    
    /**
     * 创建系统调用上下文
     */
    public static RpcCallContext newSystemContext(String taskId) {
        return newBuilder()
                .callerId(taskId)
                .callType(CallType.SYSTEM)
                .build();
    }
}