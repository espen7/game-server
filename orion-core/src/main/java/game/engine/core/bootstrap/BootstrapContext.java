package game.engine.core.bootstrap;

import org.apache.pekko.actor.ActorSystem;

import java.util.HashMap;
import java.util.Map;

/**
 * Bootstrap 启动上下文。
 * 
 * <p>封装 Bootstrap 初始化所需的依赖和配置信息。
 * 
 * <h2>职责</h2>
 * <ul>
 *   <li>提供 ActorSystem 引用</li>
 *   <li>提供实例 ID 和进程类型</li>
 *   <li>支持自定义属性传递</li>
 * </ul>
 * 
 * @since 1.0
 */
public class BootstrapContext {
    
    private final ActorSystem actorSystem;
    private final int instanceId;
    private final String processType;
    private final Map<String, Object> attributes;
    
    private BootstrapContext(Builder builder) {
        this.actorSystem = builder.actorSystem;
        this.instanceId = builder.instanceId;
        this.processType = builder.processType;
        this.attributes = new HashMap<>(builder.attributes);
    }
    
    /**
     * 获取 ActorSystem
     */
    public ActorSystem getActorSystem() {
        return actorSystem;
    }
    
    /**
     * 获取实例 ID
     */
    public int getInstanceId() {
        return instanceId;
    }
    
    /**
     * 获取进程类型
     */
    public String getProcessType() {
        return processType;
    }
    
    /**
     * 获取自定义属性
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }
    
    /**
     * 获取自定义属性（带默认值）
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key, T defaultValue) {
        return (T) attributes.getOrDefault(key, defaultValue);
    }
    
    /**
     * 检查是否包含属性
     */
    public boolean hasAttribute(String key) {
        return attributes.containsKey(key);
    }
    
    /**
     * 创建 Builder
     */
    public static Builder builder(ActorSystem actorSystem) {
        return new Builder(actorSystem);
    }
    
    /**
     * Builder 模式
     */
    public static class Builder {
        private final ActorSystem actorSystem;
        private int instanceId = 0;
        private String processType = "UNKNOWN";
        private final Map<String, Object> attributes = new HashMap<>();
        
        private Builder(ActorSystem actorSystem) {
            this.actorSystem = actorSystem;
        }
        
        public Builder instanceId(int instanceId) {
            this.instanceId = instanceId;
            return this;
        }
        
        public Builder processType(String processType) {
            this.processType = processType;
            return this;
        }
        
        public Builder attribute(String key, Object value) {
            this.attributes.put(key, value);
            return this;
        }
        
        public BootstrapContext build() {
            return new BootstrapContext(this);
        }
    }
}
