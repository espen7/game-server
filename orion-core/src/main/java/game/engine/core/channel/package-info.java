/**
 * 通用批处理通道系统。
 * 
 * <h2>核心组件</h2>
 * <ul>
 *   <li>{@link game.engine.core.channel.DeltaPublisher} - 发布者，管理所有通道</li>
 *   <li>{@link game.engine.core.channel.BatchChannel} - 批处理通道抽象基类</li>
 *   <li>{@link game.engine.core.channel.database.DatabaseChannel} - 数据库持久化通道</li>
 *   <li>{@link game.engine.core.channel.client.ClientSyncChannel} - 客户端同步通道</li>
 *   <li>{@link game.engine.core.channel.ChannelBootstrap} - 通道初始化器</li>
 * </ul>
 * 
 * <h2>使用流程</h2>
 * <pre>
 * // 1. 服务器启动时初始化（通过 AbstractServer 自动管理）
 * BootstrapManager manager = new BootstrapManager(context);
 * manager.register(new ChannelBootstrap());
 * manager.initAll();
 * 
 * // 2. 业务代码中发布变更
 * player.setNickname("Hero");
 * DeltaPublisher.getInstance().publish(player);  // 广播到所有通道
 * 
 * // 或选择性发布
 * DeltaPublisher.getInstance().publishTo(player, "database", "redis");
 * 
 * // 3. 服务器关闭时清理（通过 AbstractServer 自动管理）
 * manager.shutdownAll();
 * </pre>
 * 
 * <h2>扩展自定义通道</h2>
 * <pre>
 * public class MyChannel extends BatchChannel&lt;DeltaSnapshot&gt; {
 *     public MyChannel() {
 *         super("my-channel", 50, 2000);
 *     }
 *     
 *     &#64;Override
 *     public boolean accepts(Class&lt;?&gt; entityClass) {
 *         return Player.class.isAssignableFrom(entityClass);
 *     }
 *     
 *     &#64;Override
 *     protected void processBatch(List&lt;DeltaSnapshot&gt; batch) {
 *         // 自定义批处理逻辑
 *     }
 * }
 * 
 * // 注册自定义通道
 * DeltaPublisher.getInstance().registerChannel("my-channel", new MyChannel());
 * </pre>
 * 
 * @since 2.0
 */
package game.engine.core.channel;
