package game.engine.auth.actor;

import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.event.Logging;
import org.apache.pekko.event.LoggingAdapter;
import game.engine.core.message.Envelope;
import game.engine.core.message.Letter;

/**
 * Stateless AuthActor for handling login authentication.
 * This actor does not maintain any player state.
 */
public class AuthActor extends AbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(Envelope.class, this::handleAuth)
                .build();
    }

    private void handleAuth(Envelope envelope) {
        Letter letter = envelope.getLetter();
        log.info("Processing auth request for player: {}, msgId: {}", envelope.getPlayerId(), letter.msgId());

        // Simulate authentication logic (e.g., token validation)
        // In a real scenario, this might involve DB or external API calls.
        boolean success = validateToken(letter.payload());

        if (success) {
            log.info("Auth successful for player: {}", envelope.getPlayerId());
            // Send success response back to the sender (Gateway)
            getSender().tell("AUTH_SUCCESS", getSelf());
        } else {
            log.warning("Auth failed for player: {}", envelope.getPlayerId());
            getSender().tell("AUTH_FAILURE", getSelf());
        }
    }

    private boolean validateToken(byte[] payload) {
        // Placeholder for token validation logic
        return payload != null && payload.length > 0;
    }
}
