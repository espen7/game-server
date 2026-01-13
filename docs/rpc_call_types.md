# RPC调用类型区分使用指南

## 概述

本指南说明如何在Orion游戏服务器中区分和处理不同类型的RPC调用：

1. **客户端调用 (Client Call)**: 来自外部客户端的请求
2. **内部服务调用 (Internal Call)**: 服务间的相互调用
3. **系统调用 (System Call)**: 系统内部任务调用

## 调用类型识别机制

### 1. RpcCallContext 上下文对象

每种调用类型都有对应的上下文标识：

```java
// 客户端调用上下文
RpcCallContext clientContext = RpcCallContext.newClientContext("client-123");

// 内部服务调用上下文  
RpcCallContext internalContext = RpcCallContext.newInternalContext("gateway-service");

// 系统调用上下文
RpcCallContext systemContext = RpcCallContext.newSystemContext("cron-job-001");
```

### 2. RpcRequest 中的调用类型判断

```java
RpcRequest request = // ... 获取请求对象

// 判断调用类型
if (request.isClientCall()) {
    // 处理客户端调用
    handleClientRequest(request);
} else if (request.isInternalCall()) {
    // 处理内部服务调用
    handleInternalRequest(request);
} else if (request.isSystemCall()) {
    // 处理系统调用
    handleSystemRequest(request);
}
```

## 具体使用场景

### 场景1: Gateway处理客户端请求

```java
public class GatewayHandler {
    private GatewayRpcClient rpcClient;
    
    public void handleClientMessage(ClientMessage message) {
        // 使用客户端RPC客户端处理外部请求
        CompletableFuture<RpcResponse> future = rpcClient.handleClientRequest(
            "player-service", 
            "getPlayerInfo", 
            message.getPlayerId()
        );
        
        future.thenAccept(response -> {
            if (response.isSuccess()) {
                // 返回给客户端
                sendToClient(response.getResult());
            } else {
                // 返回错误信息
                sendClientError(response.getException());
            }
        });
    }
}
```

### 场景2: Gateway调用内部服务

```java
public class GameLogicHandler {
    private GatewayRpcClient rpcClient;
    
    public void processGameLogic(long playerId, int worldId) {
        // 使用内部RPC客户端调用服务
        boolean success = rpcClient.enterWorld(playerId, worldId);
        if (success) {
            logger.info("Player {} entered world {}", playerId, worldId);
        }
    }
    
    public CompletableFuture<PlayerInfo> loadPlayerData(long playerId) {
        // 异步获取玩家数据
        return rpcClient.getPlayerInfoAsync(playerId);
    }
}
```

### 场景3: Player服务调用World服务

```java
public class PlayerActor {
    private InternalRpcClient rpcClient;
    
    public void handleWorldInteraction(int worldId, String action) {
        // Player服务调用World服务
        rpcClient.callAsync("world-service", "processAction", worldId, action)
            .thenAccept(response -> {
                if (response.isSuccess()) {
                    // 处理世界服务响应
                    handleWorldResponse(response.getResult());
                }
            });
    }
}
```

## 安全控制差异

### 客户端调用安全控制
```java
// ClientRpcClient 自动应用的安全措施：
// 1. 权限验证 - 检查客户端是否有权调用指定服务
// 2. 速率限制 - 控制客户端调用频率
// 3. 参数验证 - 验证输入参数的安全性
// 4. 日志记录 - 详细记录客户端行为

ClientRpcClient clientRpc = new ClientRpcClient(actorSystem, "web-client-001");
// 自动应用上述安全控制
```

### 内部服务调用安全控制
```java
// InternalRpcClient 的安全措施：
// 1. 服务认证 - 验证调用方服务身份
// 2. 调用链验证 - 检查服务调用关系是否合法
// 3. 权限粒度控制 - 更细粒度的服务间权限

InternalRpcClient internalRpc = new InternalRpcClient(actorSystem, "player-service");
// 应用服务间安全控制
```

## 配置和管理

### 服务调用白名单配置
```java
// 允许的服务调用关系
private static final String[][] ALLOWED_SERVICE_CALLS = {
    {"gateway-service", "player-service"},    // Gateway可以调用Player
    {"gateway-service", "world-service"},     // Gateway可以调用World
    {"player-service", "world-service"},      // Player可以调用World
    {"world-service", "player-service"}       // World可以调用Player
};
```

### 客户端权限配置
```java
// 客户端允许调用的服务
private static final String[] ALLOWED_CLIENT_SERVICES = {
    "player-service", 
    "world-service"
};
```

## 监控和日志

### 调用类型日志记录
```java
// RpcServiceRegistry 中的调用日志
log.info("RPC call received - Type: {}, Caller: {}, Target: {}, Method: {}", 
        callType, callerId, serviceName, request.getMethodName());
```

### 性能监控指标
```java
// 可以按调用类型统计：
// - 客户端调用QPS
// - 内部服务调用延迟
// - 系统调用成功率
// - 各类型调用的错误率
```

## 最佳实践

### 1. 明确调用边界
- 客户端请求统一通过Gateway入口
- 服务间调用使用InternalRpcClient
- 系统任务使用专用的系统调用通道

### 2. 安全优先
- 客户端调用必须经过严格验证
- 内部服务调用也要实施最小权限原则
- 定期审查服务调用权限配置

### 3. 性能优化
- 合理设置不同类型调用的超时时间
- 客户端调用可适当增加重试机制
- 内部调用优先保证低延迟

### 4. 可观测性
- 为不同调用类型设置不同的日志级别
- 建立调用链追踪机制
- 实施针对性的监控告警

这个设计使得系统能够清晰地区分和管理不同来源的RPC调用，既保证了安全性又提高了系统的可维护性。