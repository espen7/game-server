package game.engine.world;

import game.engine.core.ProcessType;
import game.engine.core.bootstrap.BootstrapManager;
import game.engine.core.server.AbstractServer;
import game.engine.world.bootstrap.WorldActorBootstrap;
import org.apache.pekko.actor.ActorSystem;

/**
 * World 服务器。
 * 
 * <p>使用 Bootstrap 架构组装启动组件：
 * <ul>
 *   <li>WorldActorBootstrap - World Actor 初始化（优先级 30）</li>
 * </ul>
 */
public class WorldServer extends AbstractServer {

    public static void main(String[] args) {
        new WorldServer().boot(args);
    }

    @Override
    protected ProcessType getProcessType() {
        return ProcessType.WORLD;
    }

    @Override
    protected void registerBootstraps(BootstrapManager manager) {
        // 注册 WorldActorBootstrap（优先级 30）
        manager.register(new WorldActorBootstrap());
        
        logger.info("Registered World bootstraps: WorldActorBootstrap");
    }
}
