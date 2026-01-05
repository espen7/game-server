package game.engine.core.message;

import java.io.Serializable;

/**
 * Wraps a Letter with internal server metadata for routing and processing.
 */
public class Envelope implements Serializable {
    private final Letter letter;
    private final long uid;
    private final String gatewayId;
    private final long timestamp;

    public Envelope(Letter letter, long uid, String gatewayId) {
        this.letter = letter;
        this.uid = uid;
        this.gatewayId = gatewayId;
        this.timestamp = System.currentTimeMillis();
    }

    public Letter getLetter() {
        return letter;
    }

    public long getUid() {
        return uid;
    }

    public String getGatewayId() {
        return gatewayId;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
