package game.engine.gateway.handler;


public class MessageRouter {

    public enum Destination {
        GATEWAY,
        HOME,
        WORLD,
        UNKNOWN
    }

    public static Destination route(int msgId) {
        if (msgId < 1000) {
            return Destination.GATEWAY;
        } else if (msgId < 2000) {
            return Destination.HOME;
        } else if (msgId < 3000) {
            return Destination.WORLD;
        } else {
            return Destination.UNKNOWN;
        }
    }
}
