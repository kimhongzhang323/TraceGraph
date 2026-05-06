# 06 — Memory

Working memory in TraceGraph is the state object itself — it lives only for the duration of one execution. The `MemoryStore` SPI provides **cross-execution persistence**: data that survives beyond a single run and is accessible by any node via `ctx.memory()`.

## MemoryStore SPI

`MemoryStore` is a scoped key-value store:

```java
ctx.memory().put("user:42", "preferences", Map.of("lang", "en"));
Object prefs = ctx.memory().get("user:42", "preferences");
Set<String> keys = ctx.memory().keys("user:42");
ctx.memory().delete("user:42", "preferences");
```

The first argument is the **scope** (typically a user ID, session ID, or domain category) and the second is the key within that scope.

## Wiring the store

```java
MemoryStore store = new InMemoryMemoryStore();

Graph<ChatState> graph = Graph.<ChatState>builder()
    .node("recall",  recallNode)
    .node("respond", respondNode)
    .node("remember", rememberNode)
    .edge("recall", "respond")
    .edge("respond", "remember")
    .entry("recall")
    .terminal("remember")
    .memoryStore(store)
    .build();
```

If no store is wired, `ctx.memory()` returns a no-op implementation that discards all writes silently.

## Reading and writing in nodes

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

## FileMemoryStore and JdbcMemoryStore

For production use, swap in a durable implementation:

```java
// File-backed (useful for local development)
MemoryStore fileStore = FileMemoryStore.of(Path.of("/var/tracegraph/memory"));

// JDBC (production)
JdbcMemoryStore jdbcStore = new JdbcMemoryStore(dataSource);
jdbcStore.initSchema();
```

Both implementations support heterogeneous value types (strings, numbers, lists, maps) using Jackson polymorphic serialization. Path-traversal attacks are guarded — scope and key values containing `/`, `\`, or `..` are rejected.

## Spring Boot auto-wiring

When `tracegraph-memory`, `jackson-databind`, and a `DataSource` bean are all on the classpath, `MemoryAutoConfiguration` registers a `JdbcMemoryStore` automatically. Disable with `tracegraph.memory.jdbc.enabled=false`.

## Key takeaways

- `ctx.memory()` is scoped key-value storage that persists across executions.
- The default (no store wired) is a no-op — writes are silently discarded.
- `InMemoryMemoryStore` is suitable for tests; `FileMemoryStore` and `JdbcMemoryStore` for production.
- Scope + key pairs are validated against path-traversal attacks.
