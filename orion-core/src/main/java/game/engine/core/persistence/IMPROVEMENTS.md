# 数据库标记脏系统改进说明

## 改进概览

本次对数据库脏标记系统进行了全面重构，解决了原有设计的并发安全、生命周期管理等核心问题。

---

## ✅ 已解决的问题

### 1. 并发安全问题
**原问题**：批处理线程flush后清除脏标记，可能丢失业务线程的新变更

**解决方案**：
- 引入 `DeltaSnapshot` 快照机制
- 提交时创建快照并立即清除脏标记
- 批处理处理快照，不影响原实体

```java
// 使用方式
batchWriter.submit(player);  // 自动创建快照并清除脏标记
player.setNickname("New");    // 可以立即继续修改
```

### 2. 实体生命周期管理
**原问题**：无法区分新创建和从DB载入的实体

**解决方案**：
- 添加 `State` 枚举：TRANSIENT、MANAGED、DETACHED
- 添加生命周期方法：`onLoaded()`、`onPersisted()`、`detach()`

```java
// 新创建的实体
Player player = new Player(1001L, accountId);
player.getState();  // TRANSIENT

// 插入数据库后
mapper.insert(player);
player.onPersisted();
player.getState();  // MANAGED

// 从数据库载入
Player loaded = mapper.selectById(1001L);
loaded.onLoaded();  // 清除脏标记，标记为MANAGED
```

### 3. 虚拟线程支持
**原问题**：使用传统线程池，资源利用率低

**解决方案**：
- 使用 Java 21 虚拟线程：`Executors.newVirtualThreadPerTaskExecutor()`
- 轻量级并发，支持更高吞吐量

```java
// BatchDataWriter 现在使用虚拟线程
private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
```

### 4. 错误重试机制
**原问题**：flush失败后无重试，数据可能丢失

**解决方案**：
- 3次重试，指数退避
- 死信队列保存失败数据
- 完善的日志记录

```java
// 自动重试，失败后进入死信队列
private static final int MAX_RETRY = 3;
private static final long RETRY_DELAY_MS = 1000;
```

### 5. 乐观锁支持
**原问题**：并发更新可能覆盖数据

**解决方案**：
- 添加版本号字段
- UPDATE时自动检查版本号
- 版本不匹配则更新失败

```java
// DeltaSqlProvider 自动生成版本检查
WHERE("version = #{version}");
SET("version = version + 1");
```

---

## 🔄 完整数据流程

### 玩家登录流程
```
1. PlayerActor 收到登录请求
   ↓
2. 查询数据库
   ├─ 不存在：new Player() → INSERT → onPersisted()
   └─ 存在：selectById() → onLoaded()
   ↓
3. 玩家对象状态：MANAGED + 脏标记清空
   ↓
4. 业务修改字段
   player.setNickname("新名字")
   → markDirty(FIELD_NAME)
   → version++
   ↓
5. 提交批处理
   batchWriter.submit(player)
   → 创建 DeltaSnapshot
   → player.clearDirty()  ✅ 立即清除，允许继续修改
   ↓
6. 后台批量flush
   → 根据 State 判断 INSERT/UPDATE
   → 只更新脏字段
   → 成功后 onPersisted()
```

### 数据保存时序图
```
业务线程                  BatchDataWriter线程
   |                           |
   |  setNickname()           |
   |  -> markDirty()          |
   |                           |
   |  submit(entity)          |
   |  -> new Snapshot()       |
   |  -> clearDirty() ✅      |
   |                           |
   |  setLevel() ✅           |  processLoop()
   |  -> markDirty()          |  -> poll snapshot
   |                           |  -> flush(batch)
   |                           |  -> UPDATE SQL
   |                           |  -> commit
   |                           |
```

---

## 📝 使用指南

### 1. 实体类实现
```java
public class Player extends DeltaEntity {
    public static final int FIELD_NAME = 0;
    public static final int FIELD_LEVEL = 1;
    
    @DeltaColumn(name = "nickname", index = FIELD_NAME)
    private String nickname;
    
    @DeltaColumn(name = "lvl", index = FIELD_LEVEL)
    private int level;
    
    // Setter 中标记脏
    public void setNickname(String nickname) {
        if (!Objects.equals(this.nickname, nickname)) {
            this.nickname = nickname;
            markDirty(FIELD_NAME);
        }
    }
    
    // 实现快照收集方法
    @Override
    public Map<Integer, Object> collectDirtyValues() {
        Map<Integer, Object> values = new HashMap<>();
        if (isFieldDirty(FIELD_NAME)) {
            values.put(FIELD_NAME, nickname);
        }
        if (isFieldDirty(FIELD_LEVEL)) {
            values.put(FIELD_LEVEL, level);
        }
        return values;
    }
}
```

### 2. Actor 中使用
```java
private void handleLogin(LoginCommand cmd) {
    Player player = mapper.selectById(playerId);
    
    if (player == null) {
        // 新玩家
        player = new Player(playerId, accountId);
        mapper.insert(player);
        player.onPersisted();  // ✅ 标记为 MANAGED
    } else {
        // 已存在玩家
        player.onLoaded();     // ✅ 清除脏标记
    }
    
    // 业务逻辑
    player.setNickname("Hero");
    
    // 提交保存
    BatchDataWriter.getInstance().submit(player);
}

private void onPassivate() {
    // 被动保存
    if (player != null && player.isDirty()) {
        BatchDataWriter.getInstance().submit(player);
    }
}
```

### 3. 监控接口
```java
BatchDataWriter writer = BatchDataWriter.getInstance();

// 监控待处理队列
int pending = writer.getPendingQueueSize();

// 监控死信队列
int failed = writer.getDeadLetterQueueSize();

// 优雅关闭
writer.shutdown();
```

---

## 🎯 性能优化建议

### 1. 批处理参数调优
```java
// BatchDataWriter.java
private static final int BATCH_SIZE = 100;        // 批次大小
private static final long FLUSH_INTERVAL_MS = 5000;  // 刷新间隔
```

根据业务场景调整：
- **高并发场景**：增大 BATCH_SIZE (200-500)
- **低延迟需求**：减小 FLUSH_INTERVAL_MS (1000-2000ms)

### 2. 数据库索引
确保以下字段有索引：
```sql
CREATE INDEX idx_player_id ON player(id);
CREATE INDEX idx_player_version ON player(version);  -- 乐观锁
```

### 3. 虚拟线程监控
```java
// 监控虚拟线程使用情况
Thread.ofVirtual().name("batch-worker").start(() -> {
    // 批处理逻辑
});
```

---

## ⚠️ 注意事项

### 1. 版本号字段
如需使用乐观锁，数据库表需要添加 version 字段：
```sql
ALTER TABLE player ADD COLUMN version BIGINT DEFAULT 0;
```

### 2. 死信队列处理
当前死信队列仅记录，需要实现告警和恢复机制：
```java
// TODO: 实现死信队列处理
// - 持久化到文件
// - 发送告警通知
// - 定期重试
```

### 3. 线程安全
虽然已优化，但以下场景仍需注意：
- 不要在多个Actor间共享同一实体对象
- 实体的修改应该在Actor内完成

---

## 📊 改进前后对比

| 特性 | 改进前 | 改进后 |
|------|--------|--------|
| 并发安全 | ❌ flush时可能丢失新变更 | ✅ 快照机制完全隔离 |
| 生命周期 | ❌ 无状态管理 | ✅ TRANSIENT/MANAGED/DETACHED |
| 线程模型 | ⚠️ 传统线程 | ✅ 虚拟线程 |
| 错误处理 | ❌ 无重试机制 | ✅ 3次重试+死信队列 |
| 数据一致性 | ❌ 无乐观锁 | ✅ 版本号支持 |
| INSERT/UPDATE | ⚠️ 需手动判断 | ✅ 自动判断 |

---

## 🚀 下一步优化方向

1. **批处理Actor集成**：统一使用 BatchActor 替代独立实现
2. **动态批次大小**：根据负载自动调整
3. **分表支持**：根据ID范围路由到不同表
4. **死信队列持久化**：实现可靠的失败恢复
5. **监控指标**：集成 Metrics 监控

---

## 🔗 相关文件

- `DeltaEntity.java` - 实体基类
- `DeltaSnapshot.java` - 快照类
- `BatchDataWriter.java` - 批处理写入器
- `DeltaSqlProvider.java` - SQL生成器
- `Player.java` - 示例实体
- `PlayerActor.java` - 示例Actor

---

**更新时间**: 2026-01-16  
**版本**: v2.0
