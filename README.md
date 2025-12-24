# Orion Game Server Framework


> ⚡ Next-gen Game Backend: Leveraging Java 21 Virtual Threads and Pekko Cluster for massive scalability. High-throughput gateway with Netty Epoll & Delta-sync state management.

>  下一代游戏后端：利用 Java 21 虚拟线程和 Pekko 集群实现海量扩展。基于 Netty Epoll 的高吞吐网关及 Delta 增量状态同步。


## 为什么选择 Actor 模型? (Why Actor Model?)

Orion 采用 Actor 模型，专为游戏开发而生：

*   **无锁并发 (Lock-free Concurrency)**: 状态被封装在 Actor 内部，消息串行处理。开发者无需编写复杂的 `synchronized` 代码或担心死锁，即可保证线程安全。
*   **高吞吐与低延迟 (High Performance)**: 纯异步、非阻塞的消息驱动机制，配合 Java 21 虚拟线程，能够以极低的资源消耗处理海量并发请求。
*   **位置透明 (Location Transparency)**: 无论是本地 Actor 还是集群中的远程 Actor，交互代码完全一致。这使得服务可以轻松地在单机或多节点间迁移。
*   **容错自愈 (Fault Tolerance)**: 遵循 "Let it crash" 哲学。通过监管策略 (Supervisor Strategy)，父 Actor 可以自动重启失败的子 Actor，确保系统长期稳定运行。

## 核心特性 (Key Features)

*   **微服务架构**: 基于 Pekko Cluster 的角色分离设计 (Gateway, World, Player)，支持水平扩展。
*   **高性能网络**: 集成 **Netty 4.2** (Alpha) 处理 TCP 和 WebSocket 连接，支持高并发 I/O。
*   **分布式状态管理**: 利用 **Pekko Cluster Sharding** 自动管理玩家实体 (PlayerActor) 的分布和生命周期。
*   **Java 21 虚拟线程**: 集成虚拟线程 (Virtual Threads) 优化 Actor 调度，提升 I/O 密集型任务的吞吐量。
*   **高效同步**:
    *   **Delta 压缩**: 基于位掩码 (BitMask) 的字段级脏检查 (`DeltaEntity`)，支持嵌套对象。
    *   **Delta 合并**: 自动合并多个实体的变更 (`DeltaBuffer`)，减少网络包数量。
*   **批处理**: 基于 Actor 的批处理机制 (`BatchActor`)，无锁化处理高频数据入库或转发。
*   **热更新 (Hot Swap)**: 支持不重启服务器的情况下动态替换业务逻辑 (`HotfixClassLoader` + `FileWatcherActor`)。
*   **异步日志**: 基于 Actor 的异步日志系统，集成 Log4j2。

## 通信模型 (Communication Model)

### 1. 网络包格式 (Packet Format)
网关采用标准的 `Length + MsgId + Body` 格式：
- **Length (4 bytes)**: 整个包的长度（不含长度字段本身）。
- **MsgId (4 bytes)**: 消息号，用于标识业务类型。
- **Body (N bytes)**: 业务数据，采用 **Protocol Buffers** 序列化。

### 2. 内部消息封装 (Internal Wrapping)
为了在集群内部高效路由，Orion 采用了“信件+信封”的设计模式：
- **Letter (信件)**: 包装客户端原始消息 (`msg_id` + `payload`)。
- **Envelope (信封)**: 包装 `Letter` 并添加服务器内部元数据：
    - `player_id`: 玩家唯一标识。
    - `gateway_id`: 来源网关 ID。
    - `timestamp`: 消息到达网关的时间。

### 3. 消息路由规则
- **Gateway 内部 (MsgId < 1000)**: 如心跳、登录验证等。
- **Home 服务 (1000 <= MsgId < 2000)**: 玩家个人业务、社交等。
- **World 服务 (2000 <= MsgId < 3000)**: 场景、战斗、广播等。

## 架构概览 (Architecture)

### 模块结构
*   **orion-core**: 核心框架库。包含 Actor 系统封装、集群配置、序列化 (Delta, Batch, Hotfix) 等。
*   **orion-gateway**: 网关服务。负责维护客户端连接 (Netty)，实现协议转换与消息路由。
*   **orion-world**: 世界服务。处理地图、场景、广播等全局或区域性逻辑。支持多世界实例 (WorldID)。
*   **orion-player**: 玩家服务。承载玩家实体 (PlayerActor)，处理个人业务逻辑。

### 消息流转
1.  **接入**: 客户端通过 TCP 或 WebSocket 连接到 `orion-gateway`。
2.  **路由**:
    *   `GatewayHandler` 接收原始字节流，解码为 `Packet`。
    *   `ChannelActor` 将 `Packet` 包装为 `Envelope`，根据 `MsgId` 转发。
    *   通过 **Pekko Cluster Sharding** 或 **DistributedPubSub** 路由到目标服务。
3.  **同步**: `PlayerActor` 或 `WorldService` 产生 Delta 增量数据，通过 `Envelope` 路由回网关并推送到客户端。

## 快速开始 (Quick Start)

### 环境要求
*   JDK 21+
*   Maven 3.x

### 编译
```bash
mvn clean package
```

### 启动顺序
按顺序启动各节点 (可在不同终端):

1.  **种子节点 (Game/Cluster Seed)**:
    ```bash
    # 启动 WorldServer 实例 1
    java -jar orion-world/target/orion-world-1.0.0-SNAPSHOT.jar 1
    ```

2.  **网关节点 (Gateway)**:
    ```bash
    java -jar orion-gateway/target/orion-gateway-1.0.0-SNAPSHOT.jar
    ```

3.  **玩家节点 (Player Node)**:
    ```bash
    java -jar orion-player/target/orion-player-1.0.0-SNAPSHOT.jar
    ```

### 热更新演示
1.  运行 `game.engine.core.hotfix.demo.HotSwapDemo`。
2.  修改 `DefaultLogic.java` 源码。
3.  编译并将新的 `.class` 文件放入 `hotfix/` 目录。
4.  观察控制台输出，逻辑将自动替换。

## 技术栈
*   **Language**: Java 21
*   **Actor Model**: Apache Pekko 1.4.0
*   **Network**: Netty 4.2.9.Final
*   **Serialization**: Protocol Buffers 3.25.1
*   **Logging**: Log4j2 2.22.1


