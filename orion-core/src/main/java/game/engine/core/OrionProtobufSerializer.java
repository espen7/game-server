package game.engine.core;

import org.apache.pekko.serialization.SerializerWithStringManifest;
import com.google.protobuf.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.NotSerializableException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class OrionProtobufSerializer extends SerializerWithStringManifest {
    private static final Logger logger = LoggerFactory.getLogger(OrionProtobufSerializer.class);
    private static final String UTF_8 = "UTF-8";
    
    // Cache for parseFrom methods to avoid repeated reflection lookups
    private final Map<String, Method> parseMethods = new ConcurrentHashMap<>();

    @Override
    public int identifier() {
        return 1001; // Unique identifier for this serializer
    }

    @Override
    public String manifest(Object o) {
        return o.getClass().getName();
    }

    @Override
    public byte[] toBinary(Object o) {
        if (o instanceof Message) {
            return ((Message) o).toByteArray();
        } else {
            throw new IllegalArgumentException("OrionProtobufSerializer only supports Protobuf Messages, but got: " + o.getClass());
        }
    }

    @Override
    public Object fromBinary(byte[] bytes, String manifest) throws NotSerializableException {
        try {
            Method parseMethod = parseMethods.computeIfAbsent(manifest, this::findParseMethod);
            return parseMethod.invoke(null, bytes);
        } catch (Exception e) {
            throw new NotSerializableException("Failed to deserialize Protobuf message for manifest: " + manifest + ". Error: " + e.getMessage());
        }
    }

    private Method findParseMethod(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            return clazz.getMethod("parseFrom", byte[].class);
        } catch (Exception e) {
            throw new RuntimeException("Could not find parseFrom method for class: " + className, e);
        }
    }
}
