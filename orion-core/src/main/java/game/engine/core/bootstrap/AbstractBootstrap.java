package game.engine.core.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bootstrap 抽象基类。
 * 
 * <p>提供通用的生命周期管理和状态检查。
 * 
 * <h2>子类实现</h2>
 * <pre>
 * public class MyBootstrap extends AbstractBootstrap {
 *     
 *     public MyBootstrap() {
 *         super("MyBootstrap");
 *     }
 *     
 *     &#64;Override
 *     protected void doInit(BootstrapContext context) throws BootstrapException {
 *         // 实际的初始化逻辑
 *     }
 *     
 *     &#64;Override
 *     protected void doShutdown() throws BootstrapException {
 *         // 实际的关闭逻辑
 *     }
 * }
 * </pre>
 * 
 * @since 1.0
 */
public abstract class AbstractBootstrap implements Bootstrap {
    
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    
    private final String name;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    protected BootstrapContext context;
    
    /**
     * 构造函数
     * 
     * @param name Bootstrap 名称
     */
    protected AbstractBootstrap(String name) {
        this.name = name;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public final void init(BootstrapContext context) throws BootstrapException {
        if (initialized.compareAndSet(false, true)) {
            this.context = context;
            logger.info("Initializing bootstrap: {}", name);
            try {
                doInit(context);
                logger.info("Bootstrap initialized successfully: {}", name);
            } catch (Exception e) {
                initialized.set(false);
                logger.error("Failed to initialize bootstrap: {}", name, e);
                throw new BootstrapException("Failed to initialize " + name, e);
            }
        } else {
            logger.warn("Bootstrap already initialized: {}", name);
        }
    }
    
    @Override
    public final void shutdown() throws BootstrapException {
        if (initialized.compareAndSet(true, false)) {
            logger.info("Shutting down bootstrap: {}", name);
            try {
                doShutdown();
                logger.info("Bootstrap shut down successfully: {}", name);
            } catch (Exception e) {
                logger.error("Failed to shutdown bootstrap: {}", name, e);
                throw new BootstrapException("Failed to shutdown " + name, e);
            }
        } else {
            logger.debug("Bootstrap not initialized or already shut down: {}", name);
        }
    }
    
    @Override
    public boolean isInitialized() {
        return initialized.get();
    }
    
    /**
     * 子类实现实际的初始化逻辑
     * 
     * @param context 启动上下文
     * @throws Exception 如果初始化失败
     */
    protected abstract void doInit(BootstrapContext context) throws Exception;
    
    /**
     * 子类实现实际的关闭逻辑
     * 
     * @throws Exception 如果关闭失败
     */
    protected abstract void doShutdown() throws Exception;
}
