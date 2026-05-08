---
title: 内存
---

# 内存

> AI 翻译草稿 — 请校对。
# Memory（跨执行内存）

> AI 翻译草稿 — 请校对。

`MemoryStore` 提供跨执行的键值存储，按 `scope` 隔离。常见实现：`InMemoryMemoryStore`（测试用）、`FileMemoryStore`（JSON 持久化）、`JdbcMemoryStore`（生产）。

基本操作：

- `get(scope, key, Class<T>)`
- `put(scope, key, value)`
- `delete(scope, key)`

使用场景：共享配置、会话数据、跨执行的计数器或去重表。

示例：在节点中使用 `MemoryStore` 缓存外部调用结果：

```java
var cacheKey = "user:" + userId;
var cached = ctx.memory().get("user", cacheKey, User.class);
if (cached.isPresent()) return state.withUser(cached.get());
User u = httpClient.fetchUser(userId);
ctx.memory().put("user", cacheKey, u);
return state.withUser(u);
```

练习：使用 `FileMemoryStore` 在多个执行中存储长时运行任务的进度（例如已处理的索引位置）。
