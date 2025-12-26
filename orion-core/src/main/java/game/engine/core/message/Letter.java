package game.engine.core.message;

import java.io.Serializable;

/**
 * Represents a message from the client.
 */
public record Letter(int msgId, byte[] payload) implements Serializable {
    // pass
}
