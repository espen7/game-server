# Orion Game Server Framework

Orion 是一个基于 **Java 21** 和 **Apache Pekko 1.0** 的高性能分布式游戏服务器框架。它采用 Actor 模型实现无锁并发，支持动态扩展、故障隔离和水平扩展。

## 为什么选择 Actor 模型？ (Why Actor Model?)

Orion 使用 Actor 模型作为核心并发模型：

- **无锁并发 (Lock-free Concurrency)**：每个 Actor 内部串行处理消息。并发完全通过消息传递实现，无需 `synchronized` 或显式锁，消除竞态条件。
- **高性能高吞吐 (High Performance)**：数千万个轻量级 Actor 可并行运行；结合 Java 21 虚拟线程和异步 I/O 带来卓越的吞吐能力。
- **位置透明 (Location Transparency)**：调用本地 Actor 或远程 Actor 完全相同。你的分布式代码可以像单机代码一样自然地在集群各节点间调用。
- **容错隔离 (Fault Tolerance)**：基于 "Let it crash" 思想。通过监督树 (Supervisor Strategy) 让 Actor 在出错时只影响局部 Actor 而不是整个系统快速恢复。

## 架构概览 (Architecture)

### 子系统划分

Orion 采用微服务化架构，包含以下模块：

| 模块 | 进程类型 | 端口范围 | 职责 |
|------|---------|---------|------|
| **orion-core** | - | - | 基础 Actor 框架与公共能力 |
| **orion-gateway** | `GATEWAY` | 2551-2559 | 客户端连接、协议编解码、消息路由 |
| **orion-world** | `WORLD` | 2560-2569 | 全局逻辑、场景服务 |
| **orion-player** | `PLAYER` | 2570-2579 | 玩家个体行为处理 (Cluster Sharding) |
| **orion-portal** | `PORTAL` | 2580-2589 | 认证服务、负载均衡 |

### 进程类型管理

框架通过 `ProcessType` 枚举统一管理所有进程类型：

```java
// 启动 Gateway 实例0 (作为 seed node)
OrionEngine.create()
    .withProcessType(ProcessType.GATEWAY)
    .withPort(ProcessType.GATEWAY.getPort(0))  // 2551
    .start();

// 启动 Gateway 实例1
OrionEngine.create()
    .withProcessType(ProcessType.GATEWAY)
    .withPort(ProcessType.GATEWAY.getPort(1))  // 2552
    .withDefaultSeedNode()  // 连接到 127.0.0.1:2551
    .start();
```

**设计亮点**：
- ✅ 每个进程类型有独立的端口范围，支持多实例部署
- ✅ Gateway 实例0 (端口 2551) 作为集群的 seed node
- ✅ 类型安全，避免硬编码字符串和端口号
- ✅ 灵活的 seed node 配置：`withDefaultSeedNode()` 或 `withSeedNodes(...)`

## 核心特性 (Key Features)

### 1. 分布式集群
- 基于 **Pekko Cluster** 的多角色分层架构 (Gateway, World, Player, Portal)
- 自动服务发现和节点容错

### 2. 高性能网络
- 基于 **Netty 4.2** (Alpha) 处理 TCP 和 WebSocket
- 支持高并发 I/O

### 3. 玩家数据分片
- 使用 **Pekko Cluster Sharding** 管理数以百万计的 PlayerActor
- 动态负载均衡和故障转移

### 4. Java 21 虚拟线程
- 使用虚拟线程 (Virtual Threads) 与 Actor 模型结合
- 提升 I/O 密集型操作的性能

### 5. 高效数据同步
- **Delta Entity**：基于位掩码 (BitMask) 的增量实体 (`DeltaEntity`)，只同步变更字段
- **Delta Buffer**：管理批量对象的差异包 (`DeltaBuffer`)，减少网络带宽

### 6. 批处理任务
- 基于 Actor 的批处理引擎 (`BatchActor`)，可配置批量大小和超时策略

### 7. 热替换 (Hot Swap)
- 不停机更新代码和修复 Bug
- 通过 `HotfixClassLoader` + `FileWatcherActor` 实现类热加载

### 8. 异步日志
- 基于 Actor 的异步日志引擎，使用 Log4j2

### 9. 企业级RPC框架 (新增)
- **分层架构设计**：边缘服务提供者、网格服务调用者、核心服务编排器
- **专业命名体系**：ServiceInvoker/ServiceProvider 接口，体现企业级架构思想
- **安全访问控制**：基于调用上下文的权限验证和流量控制
- **异步非阻塞**：全面采用 CompletableFuture 实现高性能调用

## 快速开始 (Quick Start)

### 环境要求
- **JDK 21+**
- **Maven 3.6+**

### 构建项目

```bash
mvn clean install
```

### 启动服务器

**推荐启动顺序**：

```bash
# 1. 启动 Gateway 实例0 (作为 seed node)
java -jar orion-gateway/target/orion-gateway-1.0-SNAPSHOT.jar 0

# 2. 启动 World 服务器
java -jar orion-world/target/orion-world-1.0-SNAPSHOT.jar 1

# 3. 启动 Player 服务器
java -jar orion-player/target/orion-player-1.0-SNAPSHOT.jar 0

# 4. 启动 Portal 服务器
java -jar orion-portal/target/orion-portal-1.0-SNAPSHOT.jar 0 3

# 5. (可选) 启动更多 Gateway 实例实现负载均衡
java -jar orion-gateway/target/orion-gateway-1.0-SNAPSHOT.jar 1
java -jar orion-gateway/target/orion-gateway-1.0-SNAPSHOT.jar 2
```

### 命令行参数

| 服务器 | 参数 | 说明 |
|--------|------|------|
| **Gateway** | `instanceId` | 实例ID (默认0)。影响 Pekko 端口和 Netty 端口 |
| **World** | `worldId` | 世界ID (默认1)。决定端口为 2560 + (worldId-1) |
| **Player** | `instanceId` | 实例ID (默认0) |
| **Portal** | `instanceId` `actorCount` | 实例ID 和 PortalActor 数量 (默认 0 和 3) |

**示例**：

```bash
# Gateway 实例2，Pekko 端口 2553，Netty 端口 8082
java -jar orion-gateway.jar 2

# World ID 为 3，端口 2562
java -jar orion-world.jar 3

# Portal 实例1，创建 5 个 PortalActor
java -jar orion-portal.jar 1 5
```

## 技术栈 (Tech Stack)

| 技术 | 版本 | 用途 |
|------|------|------|
| **Java** | 21+ | 开发语言，虚拟线程支持 |
| **Apache Pekko** | 1.0.2 | Actor 模型、集群、分片 |
| **Netty** | 4.2.0.Alpha4 | TCP/WebSocket 网络层 |
| **Protocol Buffers** | 3.25.1 | 消息序列化 |
| **MyBatis** | 3.5.16 | 数据持久化 |
| **Log4j2** | 2.22.1 | 日志框架 |
| **Maven** | 3.6+ | 构建工具 |

## 端口分配 (Port Allocation)

| 进程类型 | 实例ID | Pekko 端口 | Netty 端口 | 说明 |
|---------|--------|-----------|-----------|------|
| **Gateway** | 0 | 2551 | 8080 | Seed Node |
| **Gateway** | 1 | 2552 | 8081 | |
| **Gateway** | 2-8 | 2553-2559 | 8082-8088 | |
| **World** | 1 | 2560 | - | |
| **World** | 2-10 | 2561-2569 | - | |
| **Player** | 0 | 2570 | - | |
| **Player** | 1-9 | 2571-2579 | - | |
| **Portal** | 0 | 2580 | - | |
| **Portal** | 1-9 | 2581-2589 | - | |

## 项目结构

```
orion-server/
├── orion-core/              # 核心模块
│   ├── config/             # 配置管理
│   ├── rpc/                # RPC框架 (新增)
│   │   ├── client/         # EdgeServiceProvider (边缘服务提供者)
│   │   ├── internal/       # MeshServiceInvoker (网格服务调用者)
│   │   ├── system/         # CoreServiceOrchestrator (核心服务编排器)
│   │   └── context/        # RpcCallContext (调用上下文)
│   ├── ProcessType         # 进程类型枚举
│   ├── OrionEngine         # 引擎启动器
│   ├── OrionContext        # 全局上下文
│   ├── actor/              # Actor 相关
│   ├── batch/              # 批处理
│   ├── hotfix/             # 热替换
│   ├── message/            # 消息封装 (Envelope, Letter)
│   ├── persistence/        # 持久化 (DeltaEntity)
│   └── sync/               # 同步 (DeltaBuffer)
├── orion-gateway/           # 网关服务
│   ├── actor/              # ChannelActor
│   ├── codec/              # Packet 编解码
│   ├── handler/            # Netty 处理器
│   └── rpc/                # GatewayRpcClient (网关RPC客户端)
├── orion-world/             # 世界服务
│   └── rpc/                # WorldRpcService (世界RPC服务)
├── orion-player/            # 玩家服务
│   └── rpc/                # PlayerRpcService (玩家RPC服务)
├── orion-portal/            # Portal 服务
└── docs/                   # 文档
    ├── rpc_design.md       # RPC设计文档
    └── rpc_professional_naming.md # RPC专业命名规范
```

## RPC框架使用指南

### 服务调用者类型

Orion RPC框架提供三种不同类型的服务调用者，适应不同场景：

#### 1. EdgeServiceProvider (边缘服务提供者)
```java
// 适用于API网关处理外部客户端请求
EdgeServiceProvider edgeProvider = new EdgeServiceProvider(actorSystem, "api-gateway-001");
CompletableFuture<RpcResponse> response = edgeProvider.callAsync("player-service", "getPlayerInfo", playerId);
```

#### 2. MeshServiceInvoker (网格服务调用者)
```java
// 适用于服务间通信
MeshServiceInvoker meshInvoker = new MeshServiceInvoker(actorSystem, "player-service");
boolean success = meshInvoker.callSync("world-service", "enterWorld", playerId, worldId);
```

#### 3. CoreServiceOrchestrator (核心服务编排器)
```java
// 适用于系统级任务和基础设施调用
CoreServiceOrchestrator orchestrator = new CoreServiceOrchestrator(actorSystem, "scheduler-service");
orchestrator.callAsync("player-service", "cleanupInactiveUsers", daysThreshold);
```

### 调用上下文管理

```java
// 创建不同类型的调用上下文
RpcCallContext clientContext = RpcCallContext.newClientContext("web-client-123");
RpcCallContext internalContext = RpcCallContext.newInternalContext("gateway-service");
RpcCallContext systemContext = RpcCallContext.newSystemContext("cron-job-001");

// 在请求中使用上下文
RpcRequest request = new RpcRequest("target-service", "methodName", params, 5000, 3, clientContext);
```

## 开发指南

### 创建自定义 Actor

```java
public class MyActor extends AbstractActor {
    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(String.class, msg -> {
                System.out.println("Received: " + msg);
                getSender().tell("Response", getSelf());
            })
            .build();
    }
}
```

### 使用 Delta Entity

```java
@DeltaColumn(index = 0)
private String name;

@DeltaColumn(index = 1)
private int level;

// 标记字段变更
markDirty(0);  // name 变更

// 生成增量包
byte[] deltaPacket = toDeltaPacket();
```

### 实现自定义RPC服务

```java
// 实现ServiceProvider接口
public class CustomService implements ServiceProvider {
    @Override
    public String getServiceName() {
        return "custom-service";
    }
    
    @Override
    public Object invokeMethod(String methodName, Object... parameters) throws RpcException {
        // 实现具体业务逻辑
        switch (methodName) {
            case "doSomething":
                return processSomething(parameters);
            default:
                throw new RpcException(RpcErrorType.METHOD_NOT_FOUND, "Method not found");
        }
    }
    
    @Override
    public boolean isAvailable() {
        return true;
    }
    
    @Override
    public String getHealthStatus() {
        return "healthy";
    }
}
```

## 性能优化建议

### 1. 虚拟线程配置
```hocon
# application.conf
pekko {
  actor {
    default-dispatcher {
      executor = "virtual-thread-executor"
    }
  }
}
```

### 2. RPC调用优化
- 优先使用异步调用避免阻塞
- 合理设置超时时间和重试策略
- 利用连接池复用RPC客户端

### 3. 集群配置
```hocon
pekko {
  cluster {
    sharding {
      # 调整分片配置以适应业务需求
      rebalance-interval = 10s
      least-shard-allocation-strategy {
        rebalance-threshold = 5
      }
    }
  }
}
```

## 监控与调试

### 1. 日志配置
```xml
<!-- log4j2.xml -->
<Logger name="game.engine.core.rpc" level="DEBUG"/>
<Logger name="org.apache.pekko.cluster" level="INFO"/>
```

### 2. JVM监控参数
```bash
-Xmx4g -Xms4g
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-Dcom.sun.management.jmxremote
```

## 故障排查

### 常见问题

1. **集群节点无法加入**
   - 检查seed node配置
   - 确认网络连通性
   - 验证端口占用情况

2. **RPC调用超时**
   - 检查服务是否正常运行
   - 调整超时配置
   - 查看网络延迟

3. **内存溢出**
   - 监控Actor数量增长
   - 检查消息积压情况
   - 优化批处理大小

## 贡献指南

欢迎提交 Issue 和 Pull Request！

### 开发约定
- 遵循Google Java Style Guide
- 单元测试覆盖率不低于80%
- 提交前运行 `mvn verify`

## 许可证 (License)

MIT License