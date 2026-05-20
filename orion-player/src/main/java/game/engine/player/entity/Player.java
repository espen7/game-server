package game.engine.player.entity;

import game.engine.core.persistence.annotation.DeltaColumn;
import game.engine.core.sync.DeltaEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class Player extends DeltaEntity {
    // 字段索引常量已移除，编译后会自动生成 PlayerFields.java

    private long id;
    private long accountId;

    @DeltaColumn(name = "nickname", index = PlayerFields.NICKNAME)
    private String nickname;

    @DeltaColumn(name = "lvl", index = PlayerFields.LEVEL)
    private int level;

    public Player(long id, long accountId) {
        this.id = id;
        this.accountId = accountId;
        this.nickname = "";
        this.level = 1;
    }

    // Required for reflection/persistence if needed, though DeltaEntity usually
    // uses constructor or setters
    public Player() {
    }

    public long getId() {
        return id;
    }

    @Override
    public long getOwnerId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getAccountId() {
        return accountId;
    }

    public void setAccountId(long accountId) {
        this.accountId = accountId;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        if (!Objects.equals(this.nickname, nickname)) {
            this.nickname = nickname;
            markDirty(PlayerFields.NICKNAME);  // 使用生成的常量
        }
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        if (this.level != level) {
            this.level = level;
            markDirty(PlayerFields.LEVEL);  // 使用生成的常量
        }
    }

    @Override
    protected void writeField(java.io.DataOutputStream out, int fieldIndex) throws java.io.IOException {
        // Implement if binary serialization is needed for delta sync
        // For DB persistence, this might not be strictly required depending on the
        // implementation
    }
    
    /**
     * 实现快照需要的collectDirtyValues方法
     */
    @Override
    public Map<Integer, Object> collectDirtyValues() {
        Map<Integer, Object> values = new HashMap<>();
        if (isFieldDirty(PlayerFields.NICKNAME)) {  // 使用生成的常量
            values.put(PlayerFields.NICKNAME, nickname);
        }
        if (isFieldDirty(PlayerFields.LEVEL)) {  // 使用生成的常量
            values.put(PlayerFields.LEVEL, level);
        }
        return values;
    }
}
