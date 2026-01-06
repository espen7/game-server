package game.engine.core;

import java.util.Arrays;
import java.util.Optional;

/**
 * 进程类型枚举，定义集群中各种服务器节点类型
 */
public enum ProcessType {
    /**
     * 网关服务器 - 处理客户端连接和协议编解码
     * 端口范围：2551-2559 (支持最多9个实例)
     * 注意：实例0 (端口2551) 通常作为集群的 seed node
     */
    GATEWAY("gateway", 2551, "Gateway Server"),
    
    /**
     * 世界服务器 - 管理全局逻辑与场景服务
     * 端口范围：2560-2569 (支持最多10个世界实例)
     */
    WORLD("world", 2560, "World Server"),
    
    /**
     * 玩家服务器 - 处理玩家个体行为
     * 端口范围：2570-2579 (支持最多10个实例)
     */
    PLAYER("player", 2570, "Player Server"),
    
    /**
     * Portal服务器 - 提供认证和负载均衡服务
     * 端口范围：2580-2589 (支持最多10个实例)
     */
    PORTAL("portal", 2580, "Portal Server");

    private final String role;
    private final int basePort;
    private final String description;

    ProcessType(String role, int basePort, String description) {
        this.role = role;
        this.basePort = basePort;
        this.description = description;
    }

    /**
     * 获取 Pekko Cluster 角色名称
     */
    public String getRole() {
        return role;
    }

    /**
     * 获取基础端口号（第一个实例的端口）
     */
    public int getBasePort() {
        return basePort;
    }

    /**
     * 根据实例ID计算端口号
     * 
     * @param instanceId 实例ID（从0开始）
     * @return 计算后的端口号
     */
    public int getPort(int instanceId) {
        if (instanceId < 0) {
            throw new IllegalArgumentException("Instance ID must be non-negative, got: " + instanceId);
        }
        return basePort + instanceId;
    }

    /**
     * 获取默认端口号（等同于实例ID为0）
     */
    public int getDefaultPort() {
        return basePort;
    }

    /**
     * 获取进程描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 根据角色名称查找进程类型
     * 
     * @param role 角色名称
     * @return 对应的进程类型
     */
    public static Optional<ProcessType> fromRole(String role) {
        if (role == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(type -> type.role.equals(role))
                .findFirst();
    }

    /**
     * 判断是否为网关节点
     */
    public boolean isGateway() {
        return this == GATEWAY;
    }

    /**
     * 判断是否为世界节点
     */
    public boolean isWorld() {
        return this == WORLD;
    }

    /**
     * 判断是否为玩家节点
     */
    public boolean isPlayer() {
        return this == PLAYER;
    }

    /**
     * 判断是否为Portal节点
     */
    public boolean isPortal() {
        return this == PORTAL;
    }

    @Override
    public String toString() {
        return String.format("%s[role=%s, basePort=%d]", name(), role, basePort);
    }
}
