# Orion Auth Module

## 线程模型设计 (Thread Model Design)

本模块采用了高性能、无状态的 Pekko Actor 模型来处理登录鉴权请求。

### 核心设计点：

1.  **无状态 Actor (Stateless AuthActor)**:
    *   `AuthActor` 内部不存储任何玩家状态。
    *   每个请求都是独立的，所有必要信息都包含在消息中。
    *   这种设计允许 Actor 被任意复用，且易于水平扩展。

2.  **Actor 路由池 (SmallestMailboxPool)**:
    *   不采用“一个玩家一个 Actor”的模式，而是维护一个固定大小的 Actor 池（Routees）。
    *   使用 `SmallestMailboxPool` 策略，将请求分发给当前任务最少的 Actor，确保负载均衡。
    *   默认配置了 16 个实例，可根据 CPU 核心数进行调整。

3.  **专用调度器 (Dedicated Dispatcher)**:
    *   配置了独立的 `auth-dispatcher`，使用 `fork-join-executor`。
    *   **舱壁模式 (Bulkhead Pattern)**: 鉴权逻辑（可能涉及 I/O）运行在独立的线程池中，即使鉴权变慢，也不会阻塞 Gateway 或 World 等核心业务模块。

4.  **吞吐量优化**:
    *   通过配置 `throughput`，减少线程上下文切换，提高处理效率。

### 配置参考 (application.conf):
```hocon
auth-dispatcher {
  type = Dispatcher
  executor = "fork-join-executor"
  fork-join-executor {
    parallelism-min = 4
    parallelism-factor = 2.0
    parallelism-max = 16
  }
  throughput = 10
}

pekko.actor.deployment {
  /authRouter {
    router = smallest-mailbox-pool
    nr-of-instances = 16
    dispatcher = auth-dispatcher
  }
}
```
