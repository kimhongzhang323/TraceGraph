---
title: 内存（概念）
---

# 内存（概念）

`MemoryStore` 是用于跨多个执行（execution）共享轻量数据的键值存储抽象，按 `scope` 隔离命名空间。它不是用来替代状态 `S`，而是用于共享配置、长时缓存、幂等去重表、或跨执行的协调数据。

1) 设计目标与契约

- 基本操作：`get(scope, key, Class<T>)`、`put(scope, key, value)`、`delete(scope, key)`、`keys(scope)`。
- `scope` 防止命名冲突；实现需要对 `scope` 与 `key` 做路径遍历保护以避免目录穿越（FileMemoryStore）。
- 默认实现 `MemoryStore.noop()` 在没有声明 store 时使用，避免空指针。

2) 常见实现

- `InMemoryMemoryStore`：基于 `ConcurrentHashMap`，适用于测试或单节点运行。
- `FileMemoryStore`：每个 `{scope}/{key}.json` 一个文件，写入通过 `*.tmp`+`ATOMIC_MOVE` 保证原子性，使用 Jackson 序列化。
- `JdbcMemoryStore`：把 `(scope, key)` 作为复合主键，`value_json` 存储序列化值；需要 `initSchema()` 来保证表存在。

3) 并发与一致性

- `InMemoryMemoryStore` 提供弱一致性（非事务），适用于无严格持久性的场景。
- `JdbcMemoryStore` 在事务中可实现强一致性（取决于底层数据库与实现）。对于重要协调，建议使用数据库-backed 实现并在业务层规范幂等。

4) 使用示例

```java
Optional<UserProfile> p = ctx.memory().get("user-profiles", userId, UserProfile.class);
if (p.isPresent()) return state.withProfile(p.get());
UserProfile profile = external.fetch(userId);
ctx.memory().put("user-profiles", userId, profile);
return state.withProfile(profile);
```

5) 注意事项与最佳实践

- 不要把大量或热数据放入 `FileMemoryStore`（文件系统开销）。
- 使用 `MemoryStore` 做去重时，把 `idempotencyKey()` 与请求细节一起记录以便回溯。
- 对于多实例部署，选择集中式 `JdbcMemoryStore`（或外部 Redis 等扩展实现）以保证跨实例可见性。

练习：在 `examples/quickstart` 中实现一个计数器，使用 `InMemoryMemoryStore` 作为演示，并记录每次执行的计数变化。
讨论 `MemoryStore` 的用途、作用域模型与常见实现注意事项（并发、原子写入、路径遍历保护）。
