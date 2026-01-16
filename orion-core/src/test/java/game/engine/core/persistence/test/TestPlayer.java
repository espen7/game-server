package game.engine.core.persistence.test;

import game.engine.core.persistence.annotation.DeltaColumn;
import game.engine.core.sync.DeltaEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 用于测试 Delta 持久化的实体。
 */
public class TestPlayer extends DeltaEntity {
    // 字段索引常量已移除，编译后会自动生成 TestPlayerFields.java

    @DeltaColumn(name = "hp", index = TestPlayerFields.HP)
    private int hp;

    @DeltaColumn(name = "player_name", index = TestPlayerFields.NAME)
    private String name;

    @DeltaColumn(name = "lvl", index = TestPlayerFields.LEVEL)
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
            markDirty(TestPlayerFields.HP);
        }
    }

    public void setName(String name) {
        if (!Objects.equals(this.name, name)) {
            this.name = name;
            markDirty(TestPlayerFields.NAME);
        }
    }

    public void setLevel(int level) {
        if (this.level != level) {
            this.level = level;
            markDirty(TestPlayerFields.LEVEL);
        }
    }

    @Override
    protected void writeField(java.io.DataOutputStream out, int fieldIndex) throws java.io.IOException {
        // 测试用，不需要实现序列化逻辑
    }
    
    @Override
    public Map<Integer, Object> collectDirtyValues() {
        Map<Integer, Object> values = new HashMap<>();
        if (isFieldDirty(TestPlayerFields.HP)) {
            values.put(TestPlayerFields.HP, hp);
        }
        if (isFieldDirty(TestPlayerFields.NAME)) {
            values.put(TestPlayerFields.NAME, name);
        }
        if (isFieldDirty(TestPlayerFields.LEVEL)) {
            values.put(TestPlayerFields.LEVEL, level);
        }
        return values;
    }
}
