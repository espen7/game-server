package game.engine.core.message;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import game.engine.core.proto.GenericProto;
import org.apache.pekko.serialization.JSerializer;

public class EnvelopeSerializer extends JSerializer {
    @Override
    public Object fromBinaryJava(byte[] bytes, Class<?> manifest) {
        GenericProto.EnvelopePb pb;
        try {
            pb = GenericProto.EnvelopePb.newBuilder().mergeFrom(bytes).build();
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
        GenericProto.LetterPb letterPb = pb.getLetter();
        Letter letter = new Letter(letterPb.getMsgId(), letterPb.getPayload().toByteArray());
        return new Envelope(letter, pb.getUid());
    }

    @Override
    public int identifier() {
        return 17175513;
    }

    @Override
    public byte[] toBinary(Object o) {
        if ((o instanceof Envelope)) {
            GenericProto.LetterPb.Builder builder = GenericProto.LetterPb.newBuilder();
            builder.setMsgId(((Envelope) o).getLetter().msgId());
            builder.setPayload(ByteString.copyFrom(((Envelope) o).getLetter().payload()));
            GenericProto.EnvelopePb pb = GenericProto.EnvelopePb.newBuilder()
                    .setLetter(builder)
                    .setUid(((Envelope) o).getUid())
                    .build();
            return pb.toByteArray();
        }
        return new byte[0];
    }

    @Override
    public boolean includeManifest() {
        return false;
    }
}
