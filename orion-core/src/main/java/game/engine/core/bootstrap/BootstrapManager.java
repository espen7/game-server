package game.engine.core.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bootstrap 管理器。
 * 
 * <p>管理多个 Bootstrap 的生命周期，按优先级顺序初始化和关闭。
 * 
 * <h2>职责</h2>
 * <ul>
 *   <li>注册 Bootstrap 组件</li>
 *   <li>按优先级顺序初始化（数字小的先初始化）</li>
 *   <li>按相反顺序关闭（后初始化的先关闭）</li>
 *   <li>处理初始化和关闭异常</li>
 * </ul>
 * 
 * <h2>使用示例</h2>
 * <pre>
 * BootstrapManager manager = new BootstrapManager(context);
 * 
 * // 注册 Bootstrap
 * manager.register(new ChannelBootstrap());
 * manager.register(new NettyBootstrap());
 * 
 * // 初始化所有
 * manager.initAll();
 * 
 * // 关闭所有
 * manager.shutdownAll();
 * </pre>
 * 
 * @since 1.0
 */
public class BootstrapManager {
    
    private static final Logger logger = LoggerFactory.getLogger(BootstrapManager.class);
    
    private final BootstrapContext context;
    private final Map<String, Bootstrap> bootstraps = new ConcurrentHashMap<>();
    private final List<Bootstrap> initializationOrder = new ArrayList<>();
    private volatile boolean initialized = false;
    
    public BootstrapManager(BootstrapContext context) {
        this.context = context;
    }
    
    /**
     * 注册 Bootstrap
     * 
     * @param bootstrap Bootstrap 实例
     * @return this（链式调用）
     * @throws IllegalArgumentException 如果 Bootstrap 名称重复
     * @throws IllegalStateException 如果已经初始化
     */
    public BootstrapManager register(Bootstrap bootstrap) {
        if (initialized) {
            throw new IllegalStateException("Cannot register bootstrap after initialization");
        }
        
        String name = bootstrap.getName();
        if (bootstraps.containsKey(name)) {
            throw new IllegalArgumentException("Bootstrap already registered: " + name);
        }
        
        bootstraps.put(name, bootstrap);
        logger.debug("Registered bootstrap: {} (priority: {})", name, bootstrap.getPriority());
        return this;
    }
    
    /**
     * 批量注册 Bootstrap
     */
    public BootstrapManager registerAll(Bootstrap... bootstraps) {
        for (Bootstrap bootstrap : bootstraps) {
            register(bootstrap);
        }
        return this;
    }
    
    /**
     * 批量注册 Bootstrap
     */
    public BootstrapManager registerAll(Collection<Bootstrap> bootstraps) {
        for (Bootstrap bootstrap : bootstraps) {
            register(bootstrap);
        }
        return this;
    }
    
    /**
     * 初始化所有 Bootstrap
     * 
     * <p>按优先级顺序（数字小的先初始化）依次初始化。
     * 如果某个 Bootstrap 初始化失败，会尝试回滚已初始化的 Bootstrap。
     * 
     * @throws BootstrapException 如果初始化失败
     */
    public synchronized void initAll() throws BootstrapException {
        if (initialized) {
            logger.warn("BootstrapManager already initialized");
            return;
        }
        
        logger.info("Initializing {} bootstraps...", bootstraps.size());
        
        // 按优先级排序
        List<Bootstrap> sorted = new ArrayList<>(bootstraps.values());
        sorted.sort(Comparator.comparingInt(Bootstrap::getPriority));
        
        // 逐个初始化
        for (Bootstrap bootstrap : sorted) {
            try {
                logger.info("Initializing bootstrap: {} (priority: {})", 
                        bootstrap.getName(), bootstrap.getPriority());
                bootstrap.init(context);
                initializationOrder.add(bootstrap);
            } catch (Exception e) {
                logger.error("Failed to initialize bootstrap: {}", bootstrap.getName(), e);
                
                // 回滚已初始化的 Bootstrap
                rollback();
                
                throw new BootstrapException(
                        "Failed to initialize bootstrap: " + bootstrap.getName(), e);
            }
        }
        
        initialized = true;
        logger.info("All bootstraps initialized successfully");
    }
    
    /**
     * 关闭所有 Bootstrap
     * 
     * <p>按初始化的相反顺序关闭（后初始化的先关闭）。
     * 即使某个 Bootstrap 关闭失败，也会继续关闭其他 Bootstrap。
     */
    public synchronized void shutdownAll() {
        if (!initialized) {
            logger.warn("BootstrapManager not initialized");
            return;
        }
        
        logger.info("Shutting down {} bootstraps...", initializationOrder.size());
        
        // 逆序关闭
        List<Bootstrap> reversed = new ArrayList<>(initializationOrder);
        Collections.reverse(reversed);
        
        List<Exception> exceptions = new ArrayList<>();
        
        for (Bootstrap bootstrap : reversed) {
            try {
                logger.info("Shutting down bootstrap: {}", bootstrap.getName());
                bootstrap.shutdown();
            } catch (Exception e) {
                logger.error("Failed to shutdown bootstrap: {}", bootstrap.getName(), e);
                exceptions.add(e);
            }
        }
        
        initializationOrder.clear();
        initialized = false;
        
        if (!exceptions.isEmpty()) {
            logger.error("Some bootstraps failed to shutdown: {} errors", exceptions.size());
        } else {
            logger.info("All bootstraps shut down successfully");
        }
    }
    
    /**
     * 回滚已初始化的 Bootstrap（私有方法）
     */
    private void rollback() {
        logger.warn("Rolling back {} initialized bootstraps...", initializationOrder.size());
        
        List<Bootstrap> reversed = new ArrayList<>(initializationOrder);
        Collections.reverse(reversed);
        
        for (Bootstrap bootstrap : reversed) {
            try {
                logger.debug("Rolling back bootstrap: {}", bootstrap.getName());
                bootstrap.shutdown();
            } catch (Exception e) {
                logger.error("Failed to rollback bootstrap: {}", bootstrap.getName(), e);
            }
        }
        
        initializationOrder.clear();
    }
    
    /**
     * 检查是否已初始化
     */
    public boolean isInitialized() {
        return initialized;
    }
    
    /**
     * 获取已注册的 Bootstrap 数量
     */
    public int getBootstrapCount() {
        return bootstraps.size();
    }
    
    /**
     * 获取所有 Bootstrap 名称
     */
    public Set<String> getBootstrapNames() {
        return new HashSet<>(bootstraps.keySet());
    }
    
    /**
     * 根据名称获取 Bootstrap
     */
    public Optional<Bootstrap> getBootstrap(String name) {
        return Optional.ofNullable(bootstraps.get(name));
    }
}
