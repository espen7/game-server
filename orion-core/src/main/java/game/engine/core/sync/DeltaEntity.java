package game.engine.core.sync;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 支持字段级 Delta 压缩的实体基类。
 * 维护一个位掩码 (BitSet) 来标记脏字段。
 * 支持嵌套实体的递归 Delta 生成。
 * 
 * 改进点：
 * 1. 添加生命周期状态管理 (TRANSIENT/MANAGED/DETACHED)
 * 2. 添加版本号支持乐观锁
 * 3. 线程安全的脏标记操作
 */
public abstract class DeltaEntity {
    
    /**
     * 实体生命周期状态
     */
    public enum State {
        /** 新创建，未持久化 */
        TRANSIENT,
        /** 已持久化，被管理中 */
        MANAGED,
        /** 已持久化，已脱管 */
        DETACHED
    }
    
    private final BitSet dirtyFlags = new BitSet();
    private final Map<Integer, DeltaEntity> nestedEntities = new HashMap<>();
    private volatile State state = State.TRANSIENT;
    private final AtomicLong version = new AtomicLong(0);

    /**
     * 标记字段为脏。
     * 
     * @param fieldIndex 字段索引
     */
    protected void markDirty(int fieldIndex) {
        synchronized (dirtyFlags) {
            dirtyFlags.set(fieldIndex);
            version.incrementAndGet();
        }
    }

    /**
     * 注册嵌套实体，以便递归检查脏状态。
     * 
     * @param fieldIndex 字段索引
     * @param entity     嵌套实体
     */
    protected void registerNested(int fieldIndex, DeltaEntity entity) {
        nestedEntities.put(fieldIndex, entity);
    }

    /**
     * 检查自身或嵌套实体是否有变更。
     */
    public boolean isDirty() {
        synchronized (dirtyFlags) {
            if (!dirtyFlags.isEmpty())
                return true;
        }
        for (DeltaEntity child : nestedEntities.values()) {
            if (child.isDirty())
                return true;
        }
        return false;
    }

    /**
     * 清除脏标记。
     */
    public void clearDirty() {
        synchronized (dirtyFlags) {
            dirtyFlags.clear();
        }
        for (DeltaEntity child : nestedEntities.values()) {
            child.clearDirty();
        }
    }

    /**
     * 检查指定字段是否为脏。
     * 
     * @param fieldIndex 字段索引
     * @return true if dirty
     */
    public boolean isFieldDirty(int fieldIndex) {
        synchronized (dirtyFlags) {
            return dirtyFlags.get(fieldIndex);
        }
    }
    
    /**
     * 获取脏标记的副本（用于快照）
     */
    public BitSet getDirtyFlagsCopy() {
        synchronized (dirtyFlags) {
            return (BitSet) dirtyFlags.clone();
        }
    }
    
    /**
     * 获取所有脏字段的值（用于快照）
     */
    public abstract Map<Integer, Object> collectDirtyValues();
    
    // ============== 生命周期管理 ==============
    
    public State getState() {
        return state;
    }
    
    public void setState(State state) {
        this.state = state;
    }
    
    public long getVersion() {
        return version.get();
    }
    
    public void setVersion(long version) {
        this.version.set(version);
    }
    
    /**
     * 当实体从数据库载入时调用
     */
    public void onLoaded() {
        this.state = State.MANAGED;
        synchronized (dirtyFlags) {
            this.dirtyFlags.clear();
        }
    }
    
    /**
     * 当实体持久化成功后调用
     */
    public void onPersisted() {
        this.state = State.MANAGED;
        synchronized (dirtyFlags) {
            this.dirtyFlags.clear();
        }
    }
    
    /**
     * 标记为脱管状态
     */
    public void detach() {
        this.state = State.DETACHED;
    }

    /**
     * 生成 Delta 数据包。
     * 格式: [MaskLen(1)][MaskBytes][FieldData...]
     * 对于嵌套实体，FieldData 为 [Length(4)][NestedDeltaBytes]
     */
    public byte[] getDelta() throws IOException {
        if (!isDirty())
            return new byte[0];

        // 1. 计算合并的脏标记 (包括嵌套实体)
        BitSet effectiveDirtyFlags = (BitSet) dirtyFlags.clone();
        for (Map.Entry<Integer, DeltaEntity> entry : nestedEntities.entrySet()) {
            if (entry.getValue().isDirty()) {
                effectiveDirtyFlags.set(entry.getKey());
            }
        }

        if (effectiveDirtyFlags.isEmpty())
            return new byte[0];

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        // 2. 写入掩码
        byte[] maskBytes = effectiveDirtyFlags.toByteArray();
        dos.writeByte(maskBytes.length);
        dos.write(maskBytes);

        // 3. 写入脏字段数据
        for (int i = effectiveDirtyFlags.nextSetBit(0); i >= 0; i = effectiveDirtyFlags.nextSetBit(i + 1)) {
            if (nestedEntities.containsKey(i)) {
                // 嵌套实体，递归写入 Delta
                byte[] childDelta = nestedEntities.get(i).getDelta();
                dos.writeInt(childDelta.length);
                dos.write(childDelta);
            } else {
                // 普通字段，由子类写入
                writeField(dos, i);
            }
        }

        return baos.toByteArray();
    }

    /**
     * 子类需实现此方法将指定字段写入流。
     * 
     * @param out        输出流
     * @param fieldIndex 字段索引
     */
    protected abstract void writeField(DataOutputStream out, int fieldIndex) throws IOException;
}
