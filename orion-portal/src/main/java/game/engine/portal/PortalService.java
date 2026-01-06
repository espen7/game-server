package game.engine.portal;

import game.engine.core.ProcessType;
import game.engine.core.OrionEngine;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import game.engine.portal.actor.PortalActor;

public class PortalService {
    public static void main(String[] args) {
        // 解析命令行参数：实例ID（默认为0）和PortalActor数量（默认为3）
        int instanceId = 0;
        int actorCount = 3;
        
        if (args.length > 0) {
            try {
                instanceId = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid instance ID, defaulting to 0");
            }
        }
        
        if (args.length > 1) {
            try {
                actorCount = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid actor count, defaulting to 3");
            }
        }

        // 启动 Portal 节点（集群模式）
        ActorSystem system = OrionEngine.create()
                .withProcessType(ProcessType.PORTAL)
                .withPort(ProcessType.PORTAL.getPort(instanceId))
                .withDefaultSeedNode() // 连接到 Gateway 实例0
                .start();

        System.out.println("Portal Service started - instance: " + instanceId + 
                ", port: " + ProcessType.PORTAL.getPort(instanceId) + 
                ", actor count: " + actorCount);

        // 创建多个 PortalActor 实例（无状态，可水平扩展）
        for (int i = 0; i < actorCount; i++) {
            ActorRef portalActor = system.actorOf(PortalActor.props(), "portal-actor-" + instanceId + "-" + i);
            System.out.println("Created PortalActor: " + portalActor.path());
        }

        System.out.println("Portal Service ready for load-balanced requests");
    }
}
