package game.engine.core.persistence.test;

import game.engine.core.persistence.annotation.DeltaColumn;
import game.engine.core.sync.DeltaEntity;

/**
 * 用于测试 Delta 持久化的实体。
 */
public class TestPlayer extends DeltaEntity {

    // 字段索引常量
    public static final int FIELD_HP = 0;
    public static final int FIELD_NAME = 1;
    public static final int FIELD_LEVEL = 2;

    @DeltaColumn(name = "hp", index = FIELD_HP)
    private int hp;

    @DeltaColumn(name = "player_name", index = FIELD_NAME)
    private String name;

    @DeltaColumn(name = "lvl", index = FIELD_LEVEL)
    private int level;

    private long id;

    public TestPlayer(long id) {
        this.id = id;
        this.name = ""; // Initialize to avoid NPE
    }

    public long getId() {
        return id;
    }

    public void setHp(int hp) {
        if (this.hp != hp) {
            this.hp = hp;
            markDirty(FIELD_HP);
        }
    }

    public void setName(String name) {
        if (this.name == null || !this.name.equals(name)) {
            this.name = name;
            markDirty(FIELD_NAME);
        }
    }

    public void setLevel(int level) {
        if (this.level != level) {
            this.level = level;
            markDirty(FIELD_LEVEL);
        }
    }

    @Override
    protected void writeField(java.io.DataOutputStream out, int fieldIndex) throws java.io.IOException {
        // 测试用，不需要实现序列化逻辑
    }
}
