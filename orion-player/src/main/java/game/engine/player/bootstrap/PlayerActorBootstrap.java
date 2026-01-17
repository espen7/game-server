package game.engine.player.bootstrap;

import game.engine.core.bootstrap.AbstractBootstrap;
import game.engine.core.bootstrap.BootstrapContext;
import game.engine.player.actor.PlayerActor;
import org.apache.pekko.actor.ActorSystem;

/**
 * Player Actor Bootstrap。
 * 
 * <p>负责初始化 Player 服务的 Actor 分片系统。
 * 
 * <h2>使用示例</h2>
 * <pre>
 * PlayerActorBootstrap bootstrap = new PlayerActorBootstrap();
 * bootstrap.init(context);
 * </pre>
 * 
 * @since 1.0
 */
public class PlayerActorBootstrap extends AbstractBootstrap {
    
    public PlayerActorBootstrap() {
        super("PlayerActorBootstrap");
    }
    
    @Override
    public int getPriority() {
        return 30; // 在 ChannelBootstrap 之前初始化
    }
    
    @Override
    protected void doInit(BootstrapContext context) throws Exception {
        ActorSystem system = context.getActorSystem();
        
        // Initialize Player Sharding
        PlayerActor.initSharding(system);
        
        logger.info("Player Actor Sharding initialized");
    }
    
    @Override
    protected void doShutdown() throws Exception {
        // Actor 分片会随 ActorSystem 一起关闭，无需显式清理
        logger.info("Player Actor Sharding will be stopped with ActorSystem");
    }
}
