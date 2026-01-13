# RPC专业命名规范文档

## 最终命名对照表

| 原始命名 | 专业化命名 | 角色定位 |
|---------|-----------|----------|
| RpcClient | ServiceInvoker | 服务调用者接口 |
| RpcService | ServiceProvider | 服务提供者接口 |
| EdgeServiceClient | EdgeServiceProvider | 边缘服务提供者 |
| MeshServiceClient | MeshServiceInvoker | 网格服务调用者 |
| CoreInfrastructureClient | CoreServiceOrchestrator | 核心服务编排器 |
| PekkoRpcClient | PekkoRpcClient | 基于Pekko的调用者实现 |

## 架构角色说明

### ServiceInvoker (服务调用者)
```
职责：发起服务调用请求
特点：主动调用方，负责请求构建和响应处理
应用场景：API网关、服务消费者、定时任务等
```

### ServiceProvider (服务提供者)  
```
职责：响应服务调用请求
特点：被动响应方，负责业务逻辑处理
应用场景：业务服务、数据服务、基础设施服务等
```

## 分层架构命名

### 第一层：边缘层 (Edge Layer)
```
EdgeServiceProvider
├── 处理外部客户端请求
├── 实施完整的安全防护
├── 适用于API网关、Web服务
└── 命名空间: game.engine.core.rpc.client
```

### 第二层：服务网格层 (Service Mesh Layer)  
```
MeshServiceInvoker
├── 处理微服务间通信
├── 实施服务网格安全策略
├── 支持流量治理和负载均衡
└── 命名空间: game.engine.core.rpc.internal
```

### 第三层：基础设施层 (Infrastructure Layer)
```
CoreServiceOrchestrator
├── 处理系统级任务调用
├── 具有最高权限级别
├── 适用于定时任务、监控、运维
└── 命名空间: game.engine.core.rpc.system
```

## 企业级价值体现

### 技术专业性
- 使用业界标准的架构术语
- 体现现代微服务设计理念
- 符合云原生发展趋势

### 商业价值
- 提升团队技术形象
- 展现架构设计能力
- 增强产品技术竞争力

### 可维护性
- 角色职责更加清晰
- 便于团队理解和维护
- 有利于架构演进和扩展

这套命名体系完全摆脱了"Client/Service"的简单表述，采用了更加专业和准确的术语，体现了企业级系统设计的成熟度。