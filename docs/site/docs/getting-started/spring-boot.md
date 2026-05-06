# Spring Boot Setup

TraceGraph ships a Spring Boot 3 starter that auto-wires SPI beans, exposes REST endpoints for traces, and optionally connects JDBC memory and LLM clients.

## Add the dependency

```xml
<dependency>
  <groupId>io.tracegraph</groupId>
  <artifactId>tracegraph-spring-boot-starter</artifactId>
  <version>0.3.0</version>
</dependency>
```

## Declare your graph as a bean

The starter does **not** register a `Graph<?>` — the state type `<S>` is yours to define.

```java
@Configuration
public class AppConfig {

    @Bean
    Graph<ChatState> chatGraph(NodeListener listener, CheckpointStore checkpoints) {
        return Graph.<ChatState>builder()
            .node("ingest", (s, ctx) -> s.withInput(s.rawInput().strip()))
            .node("respond", (s, ctx) -> s.withOutput("Echo: " + s.input()))
            .edge("ingest", "respond")
            .entry("ingest")
            .terminal("respond")
            .listener(listener)
            .checkpointStore(checkpoints)
            .build();
    }
}
```

The `NodeListener` and `CheckpointStore` beans are registered by `TraceGraphAutoConfiguration` with no-op defaults; your own `@Bean` definitions override them automatically via `@ConditionalOnMissingBean`.

## Enable the trace REST API

Add `tracegraph-observability` to your classpath and declare a `TraceStore` bean. The starter then registers `TraceController` automatically:

```xml
<dependency>
  <groupId>io.tracegraph</groupId>
  <artifactId>tracegraph-observability</artifactId>
  <version>0.3.0</version>
</dependency>
```

```java
@Bean
TraceStore<ChatState> traceStore() {
    return new InMemoryTraceStore<>();
}
```

Once the bean is present, `GET /tracegraph/traces` is live.

## Configure an LLM provider

```yaml
tracegraph:
  llm:
    provider: openai
    openai:
      api-key: ${OPENAI_API_KEY}
      model: gpt-4o-mini
```

This registers an `OpenAiLlmClient` bean that your nodes can inject.

## Key takeaways

- Declare `Graph<YourState>` as a `@Bean` — the starter wires the SPIs around it.
- No-op defaults for all four SPIs (`NodeListener`, `CheckpointStore`, `TraceRecorder`, `MemoryStore`) are overridden by your own beans.
- The trace REST endpoints appear only when a `TraceStore` bean is present on the classpath.
- LLM auto-config activates via `tracegraph.llm.provider`.
