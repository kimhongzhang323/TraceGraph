# TraceGraph

**A production-grade agent runtime for the JVM.** Typed graphs, durable memory, deep observability.

## Why TraceGraph?

- **Typed graphs** — compile-time safety through Java's type system
- **Durable memory** — cross-execution state with JDBC, file, and in-memory backends
- **Deep observability** — full trace replay, step-level diffs, and OpenTelemetry integration
- **Spring Boot ready** — auto-configuration for all SPIs

## Quick Example

```java
record MyState(String input, String output) {}

Graph<MyState> graph = Graph.<MyState>builder()
    .node("process", (state, ctx) -> new MyState(state.input(), "Processed: " + state.input()))
    .entry("process")
    .build();

var result = graph.run(new MyState("hello", null));
System.out.println(result.finalState().output()); // Processed: hello
```

## Installation

```xml
<dependency>
    <groupId>site.tracegraph</groupId>
    <artifactId>langgraph-core</artifactId>
    <version>0.2.0-SNAPSHOT</version>
</dependency>
```

## Modules

| Module | Purpose |
|--------|---------|
| `langgraph-core` | Graph engine, SPIs |
| `langgraph-runtime` | Checkpointing, retries, async |
| `langgraph-observability` | OTel, trace replay, diffs |
| `langgraph-memory` | Memory store implementations |
| `langgraph-connectors` | LLM clients, ReAct, tool-use |
| `langgraph-rag` | Vector stores, embeddings, retriever |
| `langgraph-spring-boot-starter` | Spring Boot auto-configuration |
