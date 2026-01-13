package game.engine.world.rpc;

import java.io.Serializable;

/**
 * 世界信息服务类
 */
public class WorldInfo implements Serializable {
    private final int worldId;
    private final String name;
    private final String description;
    private final int maxPlayers;
    private final int currentPlayerCount;
    private final boolean isActive;

    public WorldInfo(int worldId, String name, String description, int maxPlayers, int currentPlayerCount) {
        this.worldId = worldId;
        this.name = name;
        this.description = description;
        this.maxPlayers = maxPlayers;
        this.currentPlayerCount = currentPlayerCount;
        this.isActive = true;
    }

    // Getters
    public int getWorldId() { return worldId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getMaxPlayers() { return maxPlayers; }
    public int getCurrentPlayerCount() { return currentPlayerCount; }
    public boolean isActive() { return isActive; }

    @Override
    public String toString() {
        return String.format("WorldInfo{id=%d, name=%s, players=%d/%d}", 
                           worldId, name, currentPlayerCount, maxPlayers);
    }
}