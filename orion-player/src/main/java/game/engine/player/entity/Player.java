package game.engine.player.entity;

import game.engine.core.persistence.annotation.DeltaColumn;
import game.engine.core.sync.DeltaEntity;

public class Player extends DeltaEntity {

    public static final int FIELD_NAME = 0;
    public static final int FIELD_LEVEL = 1;

    private long id;
    private long accountId;

    @DeltaColumn(name = "nickname", index = FIELD_NAME)
    private String nickname;

    @DeltaColumn(name = "lvl", index = FIELD_LEVEL)
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
        if (this.nickname == null || !this.nickname.equals(nickname)) {
            this.nickname = nickname;
            markDirty(FIELD_NAME);
        }
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        if (this.level != level) {
            this.level = level;
            markDirty(FIELD_LEVEL);
        }
    }

    @Override
    protected void writeField(java.io.DataOutputStream out, int fieldIndex) throws java.io.IOException {
        // Implement if binary serialization is needed for delta sync
        // For DB persistence, this might not be strictly required depending on the
        // implementation
    }
}
