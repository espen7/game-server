package game.engine.core.log;

import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.Props;
import org.apache.pekko.japi.pf.ReceiveBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 异步日志 Actor�?
 * 接收日志消息并异步写�?Log4j2，避免阻塞主业务 Actor�?
 */
public class LogActor extends AbstractActor {
    private static final Logger logger = LoggerFactory.getLogger(LogActor.class);

    public static class LogMessage {
        public final String level;
        public final String message;
        public final Throwable throwable;

        public LogMessage(String level, String message) {
            this(level, message, null);
        }

        public LogMessage(String level, String message, Throwable throwable) {
            this.level = level;
            this.message = message;
            this.throwable = throwable;
        }
    }

    public static Props props() {
        return Props.create(LogActor.class, LogActor::new);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(LogMessage.class, this::handleLog)
                .build();
    }

    private void handleLog(LogMessage msg) {
        switch (msg.level.toUpperCase()) {
            case "INFO":
                logger.info(msg.message);
                break;
            case "DEBUG":
                logger.debug(msg.message);
                break;
            case "WARN":
                if (msg.throwable != null) {
                    logger.warn(msg.message, msg.throwable);
                } else {
                    logger.warn(msg.message);
                }
                break;
            case "ERROR":
                if (msg.throwable != null) {
                    logger.error(msg.message, msg.throwable);
                } else {
                    logger.error(msg.message);
                }
                break;
            default:
                logger.info(msg.message);
        }
    }
}
