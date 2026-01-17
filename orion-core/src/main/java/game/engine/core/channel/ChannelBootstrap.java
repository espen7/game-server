package game.engine.core.channel;

import game.engine.core.bootstrap.AbstractBootstrap;
import game.engine.core.bootstrap.BootstrapContext;
import game.engine.core.bootstrap.BootstrapException;
import game.engine.core.channel.database.DatabaseChannel;
import game.engine.core.channel.client.ClientSyncChannel;
import org.apache.pekko.actor.ActorSystem;

/**
 * 批处理通道初始化器。
 * 
 * <p>实现 Bootstrap 接口，支持可组装的启动架构。
 * 
 * 职责：
 * 1. 初始化所有批处理通道
 * 2. 注册到DeltaPublisher
 * 3. 管理通道生命周期
 * 
 * <h2>使用示例</h2>
 * <pre>
 * ChannelBootstrap bootstrap = new ChannelBootstrap();
 * bootstrap.init(context);
 * bootstrap.shutdown();
 * </pre>
 */
public class ChannelBootstrap extends AbstractBootstrap {
    
    private ChannelConfig config;
    
    /**
     * 默认构造函数（使用默认配置）
     */
    public ChannelBootstrap() {
        this(new ChannelConfig());
    }
    
    /**
     * 自定义配置构造函数
     */
    public ChannelBootstrap(ChannelConfig config) {
        super("ChannelBootstrap");
        this.config = config;
    }
    
    @Override
    public int getPriority() {
        return 50; // 业务组件，中等优先级
    }
    
    @Override
    protected void doInit(BootstrapContext context) throws Exception {
        ActorSystem system = context.getActorSystem();
        DeltaPublisher publisher = DeltaPublisher.getInstance();
        
        // 1. 注册数据库持久化通道
        if (config.isDatabaseEnabled()) {
            DatabaseChannel dbChannel = new DatabaseChannel(
                config.getDatabaseBatchSize(),
                config.getDatabaseFlushInterval(),
                system
            );
            publisher.registerChannel("database", dbChannel);
        }
        
        // 2. 注册客户端同步通道（需要GatewayLocator实现）
        if (config.isClientSyncEnabled() && config.getGatewayLocator() != null) {
            ClientSyncChannel clientChannel = new ClientSyncChannel(config.getGatewayLocator(), system);
            publisher.registerChannel("client-sync", clientChannel);
        }
        
        logger.info("Batch processing channels initialized: {}", publisher.getChannelNames());
    }
    
    @Override
    protected void doShutdown() throws Exception {
        DeltaPublisher.getInstance().shutdown();
    }
    
    /**
     * 通道配置类
     */
    public static class ChannelConfig {
        private boolean databaseEnabled = true;
        private int databaseBatchSize = 100;
        private long databaseFlushInterval = 5000;
        
        private boolean clientSyncEnabled = false;
        private ClientSyncChannel.GatewayLocator gatewayLocator;
        
        public boolean isDatabaseEnabled() {
            return databaseEnabled;
        }
        
        public ChannelConfig setDatabaseEnabled(boolean enabled) {
            this.databaseEnabled = enabled;
            return this;
        }
        
        public int getDatabaseBatchSize() {
            return databaseBatchSize;
        }
        
        public ChannelConfig setDatabaseBatchSize(int size) {
            this.databaseBatchSize = size;
            return this;
        }
        
        public long getDatabaseFlushInterval() {
            return databaseFlushInterval;
        }
        
        public ChannelConfig setDatabaseFlushInterval(long interval) {
            this.databaseFlushInterval = interval;
            return this;
        }
        
        public boolean isClientSyncEnabled() {
            return clientSyncEnabled;
        }
        
        public ChannelConfig setClientSyncEnabled(boolean enabled) {
            this.clientSyncEnabled = enabled;
            return this;
        }
        
        public ClientSyncChannel.GatewayLocator getGatewayLocator() {
            return gatewayLocator;
        }
        
        public ChannelConfig setGatewayLocator(ClientSyncChannel.GatewayLocator locator) {
            this.gatewayLocator = locator;
            return this;
        }
    }
}
