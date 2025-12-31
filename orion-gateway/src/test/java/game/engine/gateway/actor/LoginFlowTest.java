package game.engine.gateway.actor;

import game.engine.core.message.Letter;
import game.engine.gateway.proto.GatewayProto;
import game.engine.gateway.proto.MsgIdProto;
import game.engine.player.actor.PlayerActor;
import game.engine.player.persistence.MyBatisUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LoginFlowTest {
    static ActorSystem system;

    @BeforeAll
    public static void setup() {
        System.setProperty("test.env", "true");
        system = ActorSystem.create("LoginFlowTest");

        // Join the cluster to itself
        org.apache.pekko.cluster.Cluster cluster = org.apache.pekko.cluster.Cluster.get(system);
        cluster.join(cluster.selfAddress());

        // Initialize Sharding for PlayerActor
        // We need persistence for PlayerActor, so we might need to mock MyBatis or
        // ensure DB is reachable.
        // For this test, if DB fails, PlayerActor might stop.
        // We can try to initialize MyBatisUtil with a dummy or rely on the default
        // (which tries localhost).
        // If localhost DB is not running, this test might fail.
        // But let's try.
        try {
            MyBatisUtil.init();
        } catch (Exception e) {
            System.err.println("Warning: DB init failed, test might fail if it depends on DB: " + e.getMessage());
        }

        PlayerActor.initSharding(system);
    }

    @AfterAll
    public static void teardown() {
        TestKit.shutdownActorSystem(system);
    }

    @Test
    public void testLoginAndEnterGame() {
        new TestKit(system) {
            {
                // Mock Netty Channel
                Channel channel = mock(Channel.class);
                ChannelPipeline pipeline = mock(ChannelPipeline.class);
                io.netty.channel.ChannelFuture closeFuture = mock(io.netty.channel.ChannelFuture.class);
                when(channel.pipeline()).thenReturn(pipeline);
                when(channel.isOpen()).thenReturn(true);
                when(channel.id()).thenReturn(Mockito.mock(io.netty.channel.ChannelId.class));
                when(channel.closeFuture()).thenReturn(closeFuture);

                // Create ChannelActor
                ActorRef channelActor = system.actorOf(ChannelActor.props(channel, "gateway-1"));

                // 1. Send LoginReq
                GatewayProto.LoginReq loginReq = GatewayProto.LoginReq.newBuilder()
                        .setUsername("testuser")
                        .build();

                Letter loginLetter = new Letter(MsgIdProto.MsgId.ID_LOGIN_REQ_VALUE, loginReq.toByteArray());
                channelActor.tell(loginLetter, getRef());

                // Verify LoginResp sent to channel
                // Since ChannelActor sends directly to channel, we verify the mock
                // We expect a TextWebSocketFrame with "102|..." (ID_LOGIN_RESP = 102)
                // Wait a bit for async processing
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                }

                verify(channel, Mockito.atLeastOnce()).writeAndFlush(any(TextWebSocketFrame.class));

                // 2. Send EnterGameReq
                // We need the uid from LoginResp. In our mock, uid = hash("testuser").
                long uid = Math.abs("testuser".hashCode());

                GatewayProto.EnterGameReq enterGameReq = GatewayProto.EnterGameReq.newBuilder()
                        .setUid(uid)
                        .build();

                Letter enterGameLetter = new Letter(MsgIdProto.MsgId.ID_ENTER_GAME_REQ_VALUE,
                        enterGameReq.toByteArray());
                channelActor.tell(enterGameLetter, getRef());

                // Verify EnterGameResp sent to channel
                // ID_ENTER_GAME_RESP = 104
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                }

                verify(channel, Mockito.atLeast(2)).writeAndFlush(any(TextWebSocketFrame.class));
            }
        };
    }
}
