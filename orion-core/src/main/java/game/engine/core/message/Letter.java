package game.engine.core.message;

import java.io.Serializable;

/**
 * Represents a message from the client.
 */
public class Letter implements Serializable {
    private final int msgId;
    private final byte[] payload;

    public Letter(int msgId, byte[] payload) {
        this.msgId = msgId;
        this.payload = payload;
    }

    public int getMsgId() {
        return msgId;
    }

    public byte[] getPayload() {
        return payload;
    }
}
