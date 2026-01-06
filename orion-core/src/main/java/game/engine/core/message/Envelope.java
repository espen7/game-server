package game.engine.core.message;

import com.google.protobuf.ByteString;
import game.engine.core.proto.GenericProto;

import java.io.Serializable;

/**
 * Wraps a Letter with internal server metadata for routing and processing.
 */
public record Envelope(Letter letter, long uid, long timestamp) implements Serializable {

    public GenericProto.EnvelopePb.Builder toPb() {
        GenericProto.LetterPb.Builder letterPb = GenericProto.LetterPb.newBuilder();
        letterPb.setMsgId(letter.msgId());
        letterPb.setPayload(ByteString.copyFrom(letter.payload()));
        GenericProto.EnvelopePb.Builder envelopePb = GenericProto.EnvelopePb.newBuilder();
        envelopePb.setLetter(letterPb);
        envelopePb.setUid(uid);
        envelopePb.setTimestamp(timestamp);
        return envelopePb;
    }

    public static  Envelope fromPb(GenericProto.EnvelopePb pb) {
        GenericProto.LetterPb letterPb = pb.getLetter();
        Letter letter = new Letter(letterPb.getMsgId(), letterPb.getPayload().toByteArray());
        return new Envelope(letter, pb.getUid(), pb.getTimestamp());
    }
}
