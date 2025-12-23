package game.engine.core.sync;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

/**
 * 支持字段�?Delta 压缩的实体基类�?
 * 维护一个位掩码 (BitSet) 来标记脏字段�?
 * 支持嵌套实体的递归 Delta 生成�?
 */
public abstract class DeltaEntity {
    private final BitSet dirtyFlags = new BitSet();
    private final Map<Integer, DeltaEntity> nestedEntities = new HashMap<>();

    /**
     * 标记字段为脏�?
     * @param fieldIndex 字段索引
     */
    protected void markDirty(int fieldIndex) {
        dirtyFlags.set(fieldIndex);
    }

    /**
     * 注册嵌套实体，以便递归检查脏状态�?
     * @param fieldIndex 字段索引
     * @param entity 嵌套实体
     */
    protected void registerNested(int fieldIndex, DeltaEntity entity) {
        nestedEntities.put(fieldIndex, entity);
    }

    /**
     * 检查自身或嵌套实体是否有变更�?
     */
    public boolean isDirty() {
        if (!dirtyFlags.isEmpty()) return true;
        for (DeltaEntity child : nestedEntities.values()) {
            if (child.isDirty()) return true;
        }
        return false;
    }

    /**
     * 清除脏标记�?
     */
    public void clearDirty() {
        dirtyFlags.clear();
        for (DeltaEntity child : nestedEntities.values()) {
            child.clearDirty();
        }
    }

    /**
     * 生成 Delta 数据包�?
     * 格式: [MaskLen(1)][MaskBytes][FieldData...]
     * 对于嵌套实体，FieldData �?[Length(4)][NestedDeltaBytes]
     */
    public byte[] getDelta() throws IOException {
        if (!isDirty()) return new byte[0];

        // 1. 计算合并的脏标记 (包括嵌套实体)
        BitSet effectiveDirtyFlags = (BitSet) dirtyFlags.clone();
        for (Map.Entry<Integer, DeltaEntity> entry : nestedEntities.entrySet()) {
            if (entry.getValue().isDirty()) {
                effectiveDirtyFlags.set(entry.getKey());
            }
        }

        if (effectiveDirtyFlags.isEmpty()) return new byte[0];

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        // 2. 写入掩码
        byte[] maskBytes = effectiveDirtyFlags.toByteArray();
        dos.writeByte(maskBytes.length);
        dos.write(maskBytes);

        // 3. 写入脏字段数�?
        for (int i = effectiveDirtyFlags.nextSetBit(0); i >= 0; i = effectiveDirtyFlags.nextSetBit(i + 1)) {
            if (nestedEntities.containsKey(i)) {
                // 嵌套实体，递归写入 Delta
                byte[] childDelta = nestedEntities.get(i).getDelta();
                dos.writeInt(childDelta.length);
                dos.write(childDelta);
            } else {
                // 普通字段，由子类写�?
                writeField(dos, i);
            }
        }

        return baos.toByteArray();
    }

    /**
     * 子类需实现此方法将指定字段写入流�?
     * @param out 输出�?
     * @param fieldIndex 字段索引
     */
    protected abstract void writeField(DataOutputStream out, int fieldIndex) throws IOException;
}
