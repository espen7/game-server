package game.engine.core.bootstrap;

import org.apache.pekko.actor.ActorSystem;

/**
 * 启动组件抽象接口。
 * 
 * <p>Bootstrap 代表一个可独立初始化和关闭的组件，所有需要在服务器启动时初始化的模块
 * （如 Netty 服务器、批处理通道、RPC 客户端、数据库连接池等）都应实现此接口。
 * 
 * <h2>生命周期</h2>
 * <pre>
 * Created → init() → Running → shutdown() → Stopped
 * </pre>
 * 
 * <h2>设计原则</h2>
 * <ul>
 *   <li>每个 Bootstrap 应该是独立的、可组合的</li>
 *   <li>支持依赖注入（通过构造函数或配置对象）</li>
 *   <li>失败时应抛出明确的异常</li>
 *   <li>关闭时应正确释放资源</li>
 * </ul>
 * 
 * <h2>使用示例</h2>
 * <pre>
 * // 1. 创建 Bootstrap
 * Bootstrap nettyBootstrap = new NettyBootstrap(8080);
 * Bootstrap channelBootstrap = new ChannelBootstrap();
 * 
 * // 2. 初始化
 * nettyBootstrap.init(context);
 * channelBootstrap.init(context);
 * 
 * // 3. 关闭（逆序）
 * channelBootstrap.shutdown();
 * nettyBootstrap.shutdown();
 * </pre>
 * 
 * @see AbstractBootstrap
 * @see BootstrapManager
 * @since 1.0
 */
public interface Bootstrap {
    
    /**
     * 获取 Bootstrap 名称（用于日志和调试）
     * 
     * @return Bootstrap 名称
     */
    String getName();
    
    /**
     * 初始化组件。
     * 
     * <p>此方法应该是幂等的，重复调用应该忽略或抛出异常。
     * 
     * @param context 启动上下文，包含 ActorSystem 等依赖
     * @throws BootstrapException 如果初始化失败
     */
    void init(BootstrapContext context) throws BootstrapException;
    
    /**
     * 关闭组件并释放资源。
     * 
     * <p>此方法应该是幂等的，重复调用应该安全地忽略。
     * 
     * @throws BootstrapException 如果关闭失败
     */
    void shutdown() throws BootstrapException;
    
    /**
     * 检查是否已初始化
     * 
     * @return 如果已初始化返回 true
     */
    boolean isInitialized();
    
    /**
     * 获取启动优先级。
     * 
     * <p>数字越小优先级越高，越早初始化。关闭时按相反顺序。
     * 默认优先级为 100。
     * 
     * <p>推荐优先级分配：
     * <ul>
     *   <li>0-20: 基础设施（日志、配置）</li>
     *   <li>21-50: 核心组件（数据库、缓存）</li>
     *   <li>51-80: 业务组件（RPC、通道）</li>
     *   <li>81-100: 网络层（Netty、HTTP）</li>
     * </ul>
     * 
     * @return 优先级数字
     */
    default int getPriority() {
        return 100;
    }
}
