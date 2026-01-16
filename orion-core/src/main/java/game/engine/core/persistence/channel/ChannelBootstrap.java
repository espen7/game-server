package game.engine.core.persistence.channel;

import game.engine.core.persistence.channel.DeltaPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 批处理通道初始化器。
 * 
 * 职责：
 * 1. 初始化所有批处理通道
 * 2. 注册到DeltaPublisher
 * 3. 管理通道生命周期
 * 
 * 使用示例：
 * <pre>
 * // 服务器启动时初始化
 * ChannelBootstrap.init();
 * 
 * // 服务器关闭时清理
 * ChannelBootstrap.shutdown();
 * </pre>
 */
public class ChannelBootstrap {
    private static final Logger logger = LoggerFactory.getLogger(ChannelBootstrap.class);
    private static volatile boolean initialized = false;
    
    /**
     * 初始化所有批处理通道
     */
    public static synchronized void init() {
        if (initialized) {
            logger.warn("ChannelBootstrap already initialized");
            return;
        }
        
        logger.info("Initializing batch processing channels...");
        
        DeltaPublisher publisher = DeltaPublisher.getInstance();
        
        // 1. 注册数据库持久化通道
        DatabaseChannel dbChannel = new DatabaseChannel(100, 5000);
        publisher.registerChannel("database", dbChannel);
        
        // 2. 注册客户端同步通道（需要GatewayLocator实现）
        // ClientSyncChannel clientChannel = new ClientSyncChannel(gatewayLocator);
        // publisher.registerChannel("client-sync", clientChannel);
        
        // 3. 其他通道可以在这里注册
        // RedisChannel redisChannel = new RedisChannel();
        // publisher.registerChannel("redis", redisChannel);
        
        initialized = true;
        logger.info("Batch processing channels initialized: {}", publisher.getChannelNames());
    }
    
    /**
     * 自定义初始化（支持外部配置）
     */
    public static synchronized void init(ChannelConfig config) {
        if (initialized) {
            logger.warn("ChannelBootstrap already initialized");
            return;
        }
        
        logger.info("Initializing batch processing channels with custom config...");
        
        DeltaPublisher publisher = DeltaPublisher.getInstance();
        
        // 根据配置初始化数据库通道
        if (config.isDatabaseEnabled()) {
            DatabaseChannel dbChannel = new DatabaseChannel(
                config.getDatabaseBatchSize(),
                config.getDatabaseFlushInterval()
            );
            publisher.registerChannel("database", dbChannel);
        }
        
        // 根据配置初始化客户端同步通道
        if (config.isClientSyncEnabled() && config.getGatewayLocator() != null) {
            ClientSyncChannel clientChannel = new ClientSyncChannel(config.getGatewayLocator());
            publisher.registerChannel("client-sync", clientChannel);
        }
        
        initialized = true;
        logger.info("Batch processing channels initialized: {}", publisher.getChannelNames());
    }
    
    /**
     * 关闭所有通道
     */
    public static synchronized void shutdown() {
        if (!initialized) {
            logger.warn("ChannelBootstrap not initialized");
            return;
        }
        
        logger.info("Shutting down batch processing channels...");
        DeltaPublisher.getInstance().shutdown();
        initialized = false;
        logger.info("Batch processing channels shut down");
    }
    
    /**
     * 检查是否已初始化
     */
    public static boolean isInitialized() {
        return initialized;
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
