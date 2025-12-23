package game.engine.gateway;

import org.apache.pekko.actor.ActorSystem;
import game.engine.core.OrionEngine;
import game.engine.gateway.netty.NettyServer;
import game.engine.player.PlayerActor;

public class GatewayServer {
    public static void main(String[] args) {
        // 1. 启动 Akka System (Gateway 节点)
        ActorSystem system = OrionEngine.create()
                .withRole("gateway")
                .withPort(2552)
                .withSeedNodes("127.0.0.1:2551") // 加入 GameServer
                .start();

        // 2. 初始�?Sharding Proxy
        org.apache.pekko.cluster.sharding.ClusterSharding.get(system).startProxy(
                PlayerActor.TYPE_NAME,
                java.util.Optional.of("player"),
                PlayerActor.messageExtractor
        );

        // 3. 启动 Netty Server (WebSocket 端口 8080)
        new Thread(() -> {
            new NettyServer(8080, true, system).start();
        }).start();
        
        // 可�? 启动 TCP Server 端口 8081
        new Thread(() -> {
            new NettyServer(8081, false, system).start();
        }).start();
    }
}
