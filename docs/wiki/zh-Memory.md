# 记忆

面向智能体工作流的、作用域化的跨执行键值存储。SPI 在 `tracegraph-core`；实现在 `tracegraph-memory`。

> 🌐 本页为 AI 机翻草稿，需人工校对。English: **[[Memory]]**

## 工作记忆 vs 记忆存储

> **状态对象就是工作记忆**——它在单次执行*内*携带数据。`MemoryStore` 用于必须*跨*执行存活的数据（会话、长期事实、跨运行缓存）。

## SPI

`io.tracegraph.core.spi.MemoryStore` 是一个**每次调用显式 `scope`** 的键值存储：

```java
public interface MemoryStore {
    Optional<Object> get(String scope, String key);
    void put(String scope, String key, Object value);
    void delete(String scope, String key);
    List<String> keys(String scope);

    default List<String> pagedKeys(String scope, int offset, int limit) { /* ... */ }
    static MemoryStore noop() { /* ... */ }
}
```

- 通过 `Graph.Builder.memoryStore(...)` 接入。默认 `MemoryStore.noop()`。
- 节点通过 **`ctx.memory()`** 访问（`Context` 上默认 no-op，不破坏既有实现）。
- 实现必须**线程安全**。

```java
MemoryStore memory = new InMemoryMemoryStore();
memory.put("session:demo", "customer", Map.of("tier", "gold"));

Graph<S> graph = Graph.<S>builder()
        .memoryStore(memory)
        .node("greet", (state, ctx) -> {
            var customer = ctx.memory().get("session:demo", "customer");
            return state;
        })
        .build();
```

## 实现

| 实现 | 存储 | 备注 |
|---|---|---|
| `InMemoryMemoryStore` | 每作用域一个 `ConcurrentHashMap` | 测试、单进程 |
| `FileMemoryStore` | 根目录下每个 `{scope}/{key}` 一个 JSON 文件 | 原子 `*.tmp` + `ATOMIC_MOVE`；scope+key 路径穿越防护 |
| `JdbcMemoryStore` | 单表，任意 `DataSource` | 持久；Spring Boot 可自动接线 |

### JdbcMemoryStore

```java
JdbcMemoryStore store = JdbcMemoryStore.of(dataSource);   // 或 .of(dataSource, "tg_memory")
store.initSchema();   // 幂等
```

- 单表（默认 `tracegraph_memory`），复合主键 `(scope, key_name)`，`value_json` 列。
- 事务内可移植的 **UPDATE-then-INSERT upsert**。
- 幂等 `initSchema()`。
- 与 `FileMemoryStore` 一样使用 Jackson **default-typing-as-property**，使异构值可往返。
- 持久化失败表现为 **`MemoryPersistenceException`**。
- H2 仅用于测试。

## Spring Boot 自动配置

当存在 `DataSource` bean **且** Jackson 时，`MemoryAutoConfiguration` 自动接线 `JdbcMemoryStore`。它在 `TraceGraphAutoConfiguration` **之前**运行，从而胜过 no-op 默认。

```yaml
tracegraph:
  memory:
    jdbc:
      enabled: true          # false 跳过 JdbcMemoryStore 自动注册
      init-schema: true      # 若用 Flyway/Liquibase 管理 schema 则设 false
      table: tracegraph_memory
```

完整属性参考见 **[[zh-Spring-Boot-Integration|Spring Boot 集成]]**。

## 作用域约定

`scope` 是你自选的自由字符串。常见模式：

| 作用域形态 | 用途 |
|---|---|
| `session:{id}` | 每会话记忆 |
| `user:{id}` | 长期每用户事实 |
| `agent:{name}` | 每智能体状态（也用于 `AgentProfile.memoryScope`） |

## 已推迟内容

TTL / 过期与向量 / 语义检索是**推迟的切片**。今天的检索见 **[[zh-RAG|RAG 检索增强]]**。

---

**相关：** **[[zh-Core-Concepts|核心概念]]** · **[[zh-Multi-Agent-Patterns|多智能体模式]]** · **[[zh-RAG|RAG 检索增强]]**
