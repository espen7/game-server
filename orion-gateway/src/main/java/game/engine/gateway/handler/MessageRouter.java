package game.engine.gateway.handler;

public class MessageRouter {

    public enum Destination {
        GATEWAY,
        HOME,
        WORLD,
        UNKNOWN
    }

    public static Destination route(int msgId) {
        game.engine.gateway.proto.MsgIdProto.MsgId msgIdEnum = game.engine.gateway.proto.MsgIdProto.MsgId
                .forNumber(msgId);
        if (msgIdEnum == null) {
            return Destination.UNKNOWN;
        }

        if (msgIdEnum.getValueDescriptor().getOptions()
                .getExtension(game.engine.gateway.proto.MsgIdProto.forwardHome)) {
            return Destination.HOME;
        }

        if (msgIdEnum.getValueDescriptor().getOptions()
                .getExtension(game.engine.gateway.proto.MsgIdProto.forwardWorld)) {
            return Destination.WORLD;
        }

        return Destination.GATEWAY;
    }
}
