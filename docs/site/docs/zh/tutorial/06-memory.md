---
title: 内存
---

# 06 — 内存

TraceGraph 中的工作内存就是状态对象本身——它只在单次执行的生命周期内存在。`MemoryStore` SPI 提供**跨执行持久化**功能：数据可以在单次运行结束后继续存在，并可由任意节点通过 `ctx.memory()` 访问。

## MemoryStore SPI

`MemoryStore` 是一个按作用域分区的键值存储：

```java
ctx.memory().put("user:42", "preferences", Map.of("lang", "en"));
Object prefs = ctx.memory().get("user:42", "preferences");
Set<String> keys = ctx.memory().keys("user:42");
ctx.memory().delete("user:42", "preferences");
```

第一个参数是**作用域**（通常是用户 ID、会话 ID 或领域分类），第二个参数是该作用域内的键名。

## 连接存储

```java
MemoryStore store = new InMemoryMemoryStore();

Graph<ChatState> graph = Graph.<ChatState>builder()
    .node("recall",   recallNode)
    .node("respond",  respondNode)
    .node("remember", rememberNode)
    .edge("recall", "respond")
    .edge("respond", "remember")
    .entry("recall")
    .terminal("remember")
    .memoryStore(store)
    .build();
```

如果没有连接任何存储，`ctx.memory()` 会返回无操作实现，所有写入操作都会被静默丢弃。

## 在节点中读写

```java
Node<ChatState> recallNode = (state, ctx) -> {
    @SuppressWarnings("unchecked")
    List<String> history = (List<String>) ctx.memory()
        .get("session:" + state.sessionId(), "history");
    return state.withHistory(history != null ? history : List.of());
};

Node<ChatState> rememberNode = (state, ctx) -> {
    List<String> updated = new ArrayList<>(state.history());
    updated.add(state.lastTurn());
    ctx.memory().put("session:" + state.sessionId(), "history", List.copyOf(updated));
    return state;
};
```

## FileMemoryStore 与 JdbcMemoryStore

在生产环境中，可换用持久化实现：

```java
// 文件存储（适合本地开发）
MemoryStore fileStore = FileMemoryStore.of(Path.of("/var/tracegraph/memory"));

// JDBC 存储（生产环境）
JdbcMemoryStore jdbcStore = new JdbcMemoryStore(dataSource);
jdbcStore.initSchema();
```

两种实现均通过 Jackson 多态序列化支持异构值类型（字符串、数字、列表、Map）。路径遍历攻击已被防护——包含 `/`、`\` 或 `..` 的作用域和键值会被拒绝。

## Spring Boot 自动注入

当 `tracegraph-memory`、`jackson-databind` 和 `DataSource` Bean 均在类路径上时，`MemoryAutoConfiguration` 会自动注册一个 `JdbcMemoryStore`。可通过 `tracegraph.memory.jdbc.enabled=false` 禁用。

## 要点总结

- `ctx.memory()` 是跨执行持久化的作用域键值存储。
- 默认（未连接存储时）为无操作——写入操作会被静默丢弃。
- `InMemoryMemoryStore` 适用于测试；`FileMemoryStore` 和 `JdbcMemoryStore` 适用于生产环境。
- 作用域和键名会经过路径遍历攻击检测。
