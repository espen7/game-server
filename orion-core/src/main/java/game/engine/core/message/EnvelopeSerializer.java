package game.engine.core.message;

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
        return Envelope.fromPb(pb);
    }

    @Override
    public int identifier() {
        return 17175513;
    }

    @Override
    public byte[] toBinary(Object o) {
        if ((o instanceof Envelope)) {
            GenericProto.EnvelopePb.Builder pb = ((Envelope) o).toPb();
            return pb.build().toByteArray();
        }
        return new byte[0];
    }

    @Override
    public boolean includeManifest() {
        return false;
    }
}
