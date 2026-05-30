# Memory

Scoped, cross-execution key-value storage for agent-style workflows. The SPI lives in `tracegraph-core`; implementations live in `tracegraph-memory`.

## Working memory vs. the memory store

> **The state object is your working memory** — it carries data *within* a single execution. The `MemoryStore` is for data that must survive *across* executions (sessions, long-term facts, cross-run caches).

## The SPI

`io.tracegraph.core.spi.MemoryStore` is a key-value store with an **explicit `scope` per call**:

```java
public interface MemoryStore {
    Optional<Object> get(String scope, String key);
    void put(String scope, String key, Object value);
    void delete(String scope, String key);
    List<String> keys(String scope);

    // Backwards-compatible default; JDBC overrides with a backend LIMIT/OFFSET query
    default List<String> pagedKeys(String scope, int offset, int limit) { /* ... */ }

    static MemoryStore noop() { /* ... */ }
}
```

- Wire it in via `Graph.Builder.memoryStore(...)`. The default is `MemoryStore.noop()`.
- Nodes access it through **`ctx.memory()`** (a no-op default on `Context` so existing impls aren't broken).
- Implementations must be **thread-safe**.

```java
MemoryStore memory = new InMemoryMemoryStore();
memory.put("session:demo", "customer", Map.of("tier", "gold"));

Graph<S> graph = Graph.<S>builder()
        .memoryStore(memory)
        .node("greet", (state, ctx) -> {
            var customer = ctx.memory().get("session:demo", "customer");
            // ...
            return state;
        })
        /* ... */
        .build();
```

## Implementations

| Implementation | Storage | Notes |
|---|---|---|
| `InMemoryMemoryStore` | `ConcurrentHashMap` per scope | tests, single-process |
| `FileMemoryStore` | one JSON file per `{scope}/{key}` under a root dir | atomic `*.tmp` + `ATOMIC_MOVE`; path-traversal guard on scope+key |
| `JdbcMemoryStore` | single table, any `DataSource` | durable; auto-wirable in Spring Boot |

### FileMemoryStore

One JSON file per `{scope}/{key}` under a root directory. Uses Jackson **default-typing-as-property** so heterogeneous values round-trip. Writes go through a `*.tmp` sibling + `ATOMIC_MOVE` for crash safety, with a path-traversal guard on both scope and key. Jackson is an optional dependency.

### JdbcMemoryStore

Durable scoped key-value memory backed by any JDBC `DataSource`:

```java
JdbcMemoryStore store = JdbcMemoryStore.of(dataSource);   // or .of(dataSource, "tg_memory")
store.initSchema();   // idempotent
```

- Single table (default `tracegraph_memory`) with a composite `(scope, key_name)` primary key and a `value_json` column.
- Portable **UPDATE-then-INSERT upsert** in a transaction.
- Idempotent `initSchema()`.
- Uses the same Jackson **default-typing-as-property** as `FileMemoryStore`, so heterogeneous values round-trip.
- Persistence failures surface as **`MemoryPersistenceException`**.
- H2 is test-only.

## Spring Boot auto-configuration

`MemoryAutoConfiguration` auto-wires a `JdbcMemoryStore` when a `DataSource` bean **and** Jackson are present. It runs **before** `TraceGraphAutoConfiguration` so it wins over the no-op default.

```yaml
tracegraph:
  memory:
    jdbc:
      enabled: true          # set false to skip JdbcMemoryStore auto-registration
      init-schema: true      # set false if you manage schema with Flyway/Liquibase
      table: tracegraph_memory
```

See **[[Spring Boot Integration]]** for the full property reference.

## Scope conventions

`scope` is a free-form string you choose. Common patterns:

| Scope shape | Use |
|---|---|
| `session:{id}` | per-conversation/session memory |
| `user:{id}` | long-term per-user facts |
| `agent:{name}` | per-agent state (also used by `AgentProfile.memoryScope`) |

## What's deferred

TTL / expiry and vector / semantic search are **deferred slices** that build on this substrate. For retrieval today, see **[[RAG]]**.

---

**Related:** **[[Core Concepts]]** · **[[Multi-Agent Patterns]]** (agent memory isolation) · **[[RAG]]**
