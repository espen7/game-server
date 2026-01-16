package game.engine.core.sync;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

/**
 * DeltaEntity的快照，用于解决并发安全问题。
 * 
 * 设计思路：
 * 1. 在提交到批处理队列时创建快照
 * 2. 快照创建后立即清除原实体的脏标记，允许继续修改
 * 3. 批处理线程处理快照，不会影响原实体
 */
public class DeltaSnapshot {
    private final DeltaEntity entity;
    private final BitSet dirtyFlags;
    private final Map<Integer, Object> dirtyValues;
    private final long version;
    private final DeltaEntity.State state;
    private final Class<?> entityClass;
    
    /**
     * 创建实体快照
     * 
     * @param entity 源实体
     */
    public DeltaSnapshot(DeltaEntity entity) {
        this.entity = entity;
        this.dirtyFlags = entity.getDirtyFlagsCopy();
        this.dirtyValues = entity.collectDirtyValues();
        this.version = entity.getVersion();
        this.state = entity.getState();
        this.entityClass = entity.getClass();
    }
    
    public DeltaEntity getEntity() {
        return entity;
    }
    
    public BitSet getDirtyFlags() {
        return dirtyFlags;
    }
    
    public Map<Integer, Object> getDirtyValues() {
        return dirtyValues;
    }
    
    public long getVersion() {
        return version;
    }
    
    public DeltaEntity.State getState() {
        return state;
    }
    
    public Class<?> getEntityClass() {
        return entityClass;
    }
    
    /**
     * 是否需要INSERT操作
     */
    public boolean isInsert() {
        return state == DeltaEntity.State.TRANSIENT;
    }
    
    /**
     * 是否需要UPDATE操作
     */
    public boolean isUpdate() {
        return state == DeltaEntity.State.MANAGED && !dirtyFlags.isEmpty();
    }
    
    /**
     * 获取指定字段的快照值
     */
    public Object getFieldValue(int fieldIndex) {
        return dirtyValues.get(fieldIndex);
    }
    
    /**
     * 检查字段是否为脏
     */
    public boolean isFieldDirty(int fieldIndex) {
        return dirtyFlags.get(fieldIndex);
    }
}
