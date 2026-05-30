# Spring Boot Integration

`tracegraph-spring-boot-starter` provides auto-configuration, REST endpoints, and dependency injection. It depends only on `spring-boot-autoconfigure`; the observability, connectors, and web pieces are **optional** and activate conditionally.

> Spring Boot is pinned to **3.3.5** in the parent BOM. Spring imports are confined to this module — core stays framework-free.

## What gets auto-configured

| Auto-config | Registers | Guard |
|---|---|---|
| `TraceGraphAutoConfiguration` | no-op beans for `NodeListener`, `CheckpointStore`, `TraceRecorder`, `MemoryStore` | each `@ConditionalOnMissingBean` |
| `TraceWebAutoConfiguration` (`boot.web`) | `TraceController`, `TraceReplayController`, `TraceStreamController` | web app + `TraceStore` present + `tracegraph.web.enabled` |
| `MemoryAutoConfiguration` (`boot.memory`) | `JdbcMemoryStore` | `DataSource` + Jackson present |
| `LlmAutoConfiguration` (`boot.llm`) | `OpenAiLlmClient` / `AnthropicLlmClient` | `tracegraph-connectors` on classpath + provider set |
| `EmbeddingAutoConfiguration` | embedding client | `tracegraph.rag.embedding.provider` set |
| `A2AAutoConfiguration` | `InMemoryAgentBus`, `A2AController` | `tracegraph-a2a` present |

Each SPI bean is guarded by `@ConditionalOnMissingBean`, so **your beans always win**.

## You define the Graph

`Graph<?>` is **not** auto-registered — it is generic in `S`, so you declare your own bean and inject the SPI beans:

```java
@Configuration
public class GraphConfig {

    @Bean
    public Graph<AgentState> agentGraph(
            LlmClient llmClient,            // from LlmAutoConfiguration
            MemoryStore memoryStore,        // from MemoryAutoConfiguration (or no-op)
            TraceRecorder traceRecorder) {

        ChatNode<AgentState> chat = new ChatNode<>(
                llmClient,
                state -> new LlmRequest("gpt-4o", state.messages(), 0.7, 1024),
                (state, resp) -> state.withAnswer(resp.content()));

        return Graph.<AgentState>builder()
                .node("chat", chat)
                .entry("chat").terminal("chat")
                .memoryStore(memoryStore)
                .traceRecorder(traceRecorder)
                .build();
    }
}
```

## Configuration properties

All properties use the `tracegraph.*` prefix. Set them in `application.yml` or `application.properties`.

### Web / REST API

| Property | Type | Default | Description |
|---|---|---|---|
| `tracegraph.web.enabled` | boolean | `true` | `false` suppresses **all** web auto-config — no `TraceController`, `TraceReplayController`, or `TraceStreamController`. |
| `tracegraph.ui.enabled` | boolean | `true` | `false` suppresses the UI auto-config and `/tracegraph/ui/*` endpoints. |

### Memory (JDBC)

| Property | Type | Default | Description |
|---|---|---|---|
| `tracegraph.memory.jdbc.enabled` | boolean | `true` | `false` skips `JdbcMemoryStore` auto-registration. |
| `tracegraph.memory.jdbc.init-schema` | boolean | `true` | calls `initSchema()` on startup; `false` if you manage migrations (Flyway/Liquibase). |
| `tracegraph.memory.jdbc.table` | string | `tracegraph_memory` | override the table name. |

`MemoryAutoConfiguration` runs **before** `TraceGraphAutoConfiguration` so the JDBC store wins over the no-op default.

### LLM providers

`LlmAutoConfiguration` registers an `LlmClient` only when `tracegraph.llm.provider` is set. Guarded by `@ConditionalOnMissingBean(LlmClient)`.

| Property | Type | Default | Description |
|---|---|---|---|
| `tracegraph.llm.enabled` | boolean | `true` | `false` disables LLM auto-config entirely. |
| `tracegraph.llm.provider` | string | (none) | `openai` or `anthropic`. No bean when unset. |
| `tracegraph.llm.openai.api-key` | string | — | required when `provider=openai` |
| `tracegraph.llm.openai.model` | string | `gpt-4o-mini` | |
| `tracegraph.llm.openai.base-url` | string | `https://api.openai.com/v1` | override for compatible endpoints |
| `tracegraph.llm.openai.timeout-seconds` | int | `30` | |
| `tracegraph.llm.anthropic.api-key` | string | — | required when `provider=anthropic` |
| `tracegraph.llm.anthropic.model` | string | `claude-3-5-haiku-20241022` | |
| `tracegraph.llm.anthropic.anthropic-version` | string | `2023-06-01` | header value |
| `tracegraph.llm.anthropic.timeout-seconds` | int | `60` | LLM calls can be slow — set conservatively |

### Example

```yaml
tracegraph:
  web:
    enabled: true
  memory:
    jdbc:
      enabled: true
      init-schema: false      # managed by Flyway
      table: tg_memory
  llm:
    provider: openai
    openai:
      api-key: ${OPENAI_API_KEY}
      model: gpt-4o
      timeout-seconds: 45
```

## Not auto-wired (on purpose)

`JdbcCheckpointStore` and `JdbcTraceStore` are **not** auto-wired because they require a user-supplied `Class<S>`. Declare them as explicit `@Bean` definitions:

```java
@Bean
TraceStore traceStore(DataSource ds) {
    JdbcTraceStore<AgentState> store = JdbcTraceStore.of(ds, AgentState.class);
    store.initSchema();
    return store;
}
```

## REST endpoints

When the observability module and a `TraceStore` bean are present, the starter exposes trace, replay, resume, and streaming endpoints under `/tracegraph/*`. Full reference: **[[REST API Reference]]**.

Quick list:

- `GET /tracegraph/traces` (paginated; `X-Total-Count` header)
- `GET /tracegraph/traces/{id}`
- `GET /tracegraph/traces/{a}/diff/{b}`
- `DELETE /tracegraph/traces/{id}`
- `POST /tracegraph/traces/{id}/replay?step=N`
- `POST /tracegraph/traces/{id}/resume`
- `POST /tracegraph/traces/stream` (SSE)

The replay/resume/stream controllers require a **single** `Graph<?>` bean (`@ConditionalOnSingleCandidate(Graph.class)`).

## Runnable example

`examples/spring-boot-app` shows the starter end-to-end:

```bash
mvn -f examples/spring-boot-app/pom.xml spring-boot:run
```

---

**Related:** **[[REST API Reference]]** · **[[Memory]]** · **[[LLM Connectors]]** · **[[Observability and Replay]]**
