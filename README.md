# TraceGraph

A production-grade agent runtime for the JVM — typed graphs, durable memory, deep observability. Not a LangGraph clone; the goal is reliability, debuggability, and enterprise readiness.

## Modules

| Module | Status | Purpose |
|---|---|---|
| `langgraph-core` | Phase 1 ✅ | Typed `Graph`/`Node`/`Edge`/`State`, sync execution, conditional edges, listener SPI |
| `langgraph-runtime` | Phase 2 (skeleton) | Checkpointing, retries, async/parallel nodes, resumable execution |
| `langgraph-observability` | Phase 3 (skeleton) | OpenTelemetry, state-diff tracking, replay engine |
| `langgraph-memory` | Phase 4 (skeleton) | Working / session / long-term / semantic memory |
| `langgraph-spring-boot-starter` | Phase 5 (skeleton) | Auto-config, DI for nodes, REST trigger |
| `langgraph-connectors` | Later | LLM and vector store adapters |

## Build

Requires JDK 21 + Maven 3.9+.

```bash
mvn test
```

## Sample

```java
record OrderState(String id, boolean valid, boolean charged, boolean shipped) {
    OrderState withValid(boolean v)   { return new OrderState(id, v, charged, shipped); }
    OrderState withCharged(boolean c) { return new OrderState(id, valid, c, shipped); }
    OrderState withShipped(boolean s) { return new OrderState(id, valid, charged, s); }
}

Graph<OrderState> graph = Graph.<OrderState>builder()
    .node("validate", (state, ctx) -> state.withValid(true))
    .node("charge",   (state, ctx) -> state.withCharged(true))
    .node("ship",     (state, ctx) -> state.withShipped(true))
    .entry("validate")
    .edge("validate", "charge", OrderState::valid)
    .edge("charge", "ship")
    .terminal("ship")
    .build();

ExecutionResult<OrderState> result = graph.run(new OrderState("o-1", false, false, false));
// result.path()   == ["validate", "charge", "ship"]
// result.status() == COMPLETED
```
