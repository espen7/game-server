package game.engine.core.sync;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Delta 数据缓冲区，用于合并多个实体的 Delta 数据。
 * 格式: [EntityCount(4)] [EntityID(4) + DeltaLen(4) + DeltaBytes]...
 */
public class DeltaBuffer {
    private final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    private final DataOutputStream dos = new DataOutputStream(baos);
    private int count = 0;

    /**
     * 添加一个实体的 Delta 数据。
     * 
     * @param entityId 实体 ID
     * @param entity   实体对象
     */
    public void addEntity(int entityId, DeltaEntity entity) throws IOException {
        if (entity.isDirty()) {
            byte[] delta = entity.getDelta();
            if (delta.length > 0) {
                dos.writeInt(entityId);
                dos.writeInt(delta.length);
                dos.write(delta);
                count++;
            }
        }
    }

    /**
     * 获取最终的合并字节数组。
     * 包含头部 [Count(4)]
     */
    public byte[] toBytes() {
        try {
            ByteArrayOutputStream finalBaos = new ByteArrayOutputStream();
            DataOutputStream finalDos = new DataOutputStream(finalBaos);
            finalDos.writeInt(count);
            finalDos.write(baos.toByteArray());
            return finalBaos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create delta buffer bytes", e);
        }
    }

    public int getCount() {
        return count;
    }

    public void clear() {
        baos.reset();
        count = 0;
    }
}
