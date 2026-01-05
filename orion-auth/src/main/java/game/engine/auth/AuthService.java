package game.engine.auth;

import game.engine.core.OrionEngine;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import game.engine.auth.actor.AuthActor;

public class AuthService {
    public static void main(String[] args) {
        // 解析命令行参数：实例数量（默认 3 个）
        int instanceCount = 3;
        if (args.length > 0) {
            try {
                instanceCount = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid instance count, defaulting to 3");
            }
        }

        // 启动 Auth 节点（集群模式）
        ActorSystem system = OrionEngine.create()
                .withRole("auth")
                .withPort(2555)
                .withSeedNodes("127.0.0.1:2551") // 加入集群
                .start();

        System.out.println("Auth Service started with " + instanceCount + " instances");

        // 创建多个 AuthActor 实例（无状态，可水平扩展）
        for (int i = 0; i < instanceCount; i++) {
            ActorRef authActor = system.actorOf(AuthActor.props(), "auth-actor-" + i);
            System.out.println("Created AuthActor: " + authActor.path());
        }

        System.out.println("Auth Service ready for load-balanced requests");
    }
}
