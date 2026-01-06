# Orion Portal Module

## 线程模型设计 (Thread Model Design)

本模块采用了高性能、无状态的 Pekko Actor 模型来处理对外服务请求（鉴权、HTTP API、SDK、翻译等）。

### 核心设计点：
```
orion-portal/
  ├── auth/             # 当前的鉴权功能
  ├── api/              # RESTful API（充值、查询等）
  ├── sdk/              # SDK 支持（客户端库）
  ├── translation/      # 翻译服务
  └── integration/      # 第三方集成（支付回调等）

```

1.  **无状态 Actor (Stateless PortalActor)**:
    *   `PortalActor` 内部不存储任何状态。
    *   每个请求都是独立的，所有必要信息都包含在消息中。
    *   这种设计允许 Actor 被任意复用，且易于水平扩展。

2.  **Actor 路由池 (SmallestMailboxPool)**:
    *   不采用"一个请求一个 Actor"的模式，而是维护一个固定大小的 Actor 池（Routees）。
    *   使用 `SmallestMailboxPool` 策略，将请求分发给当前任务最少的 Actor，确保负载均衡。
    *   默认配置了 16 个实例，可根据 CPU 核心数进行调整。

3.  **专用调度器 (Dedicated Dispatcher)**:
    *   配置了独立的 `portal-dispatcher`，使用 `fork-join-executor`。
    *   **舱壁模式 (Bulkhead Pattern)**: Portal 逻辑（可能涉及 I/O）运行在独立的线程池中，即使某些服务变慢，也不会阻塞 Gateway 或 World 等核心业务模块。

4.  **吞吐量优化**:
    *   通过配置 `throughput`，减少线程上下文切换，提高处理效率。

### 配置参考 (application.conf):
```hocon
portal-dispatcher {
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
  /portalRouter {
    router = smallest-mailbox-pool
    nr-of-instances = 16
    dispatcher = portal-dispatcher
  }
}
```

## 模块职责

`orion-portal` 是对外服务的统一入口，负责：
- **认证服务**: 登录鉴权、Token 验证
- **HTTP API**: RESTful 接口、充值查询等
- **SDK 支持**: 客户端 SDK 对接
- **翻译服务**: 多语言翻译
- **第三方集成**: 支付回调、Webhook 等
