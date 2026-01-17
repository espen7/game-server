package game.engine.player;

import game.engine.core.ProcessType;
import game.engine.core.bootstrap.BootstrapManager;
import game.engine.core.channel.ChannelBootstrap;
import game.engine.core.server.AbstractServer;
import game.engine.player.bootstrap.PlayerActorBootstrap;
import org.apache.pekko.actor.ActorSystem;

/**
 * Player 服务器。
 * 
 * <p>使用 Bootstrap 架构组装启动组件：
 * <ul>
 *   <li>PlayerActorBootstrap - Player Actor 分片系统（优先级 30）</li>
 *   <li>ChannelBootstrap - 批处理通道系统（优先级 50）</li>
 * </ul>
 */
public class PlayerServer extends AbstractServer {

    public static void main(String[] args) {
        new PlayerServer().boot(args);
    }

    @Override
    protected ProcessType getProcessType() {
        return ProcessType.PLAYER;
    }
    
    @Override
    protected void registerBootstraps(BootstrapManager manager) {
        // 1. 注册 PlayerActorBootstrap（优先级 30）
        manager.register(new PlayerActorBootstrap());
        
        // 2. 注册 ChannelBootstrap（优先级 50）
        manager.register(new ChannelBootstrap());
        
        logger.info("Registered Player bootstraps: PlayerActorBootstrap, ChannelBootstrap");
    }
}
