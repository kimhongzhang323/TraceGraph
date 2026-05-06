# Configuration Reference

All TraceGraph properties use the `tracegraph.*` prefix. Set them in `application.yml` or `application.properties`.

---

## Web / REST API

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `tracegraph.web.enabled` | boolean | `true` | When `false`, suppresses the entire `TraceWebAutoConfiguration` — no `TraceController`, `TraceReplayController`, or `TraceStreamController` beans are registered. |

**Example — disable the REST API entirely:**

```yaml
tracegraph:
  web:
    enabled: false
```

---

## Trace UI

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `tracegraph.ui.enabled` | boolean | `true` | When `false`, suppresses the UI auto-config. The `/tracegraph/ui/*` endpoints and static assets are not served. |

---

## Memory (JDBC)

These properties control `MemoryAutoConfiguration`, which registers a `JdbcMemoryStore` when `tracegraph-memory`, `jackson-databind`, and a `DataSource` bean are all present.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `tracegraph.memory.jdbc.enabled` | boolean | `true` | Set to `false` to skip `JdbcMemoryStore` auto-registration. The no-op `MemoryStore` from `TraceGraphAutoConfiguration` is used instead (unless you declare your own bean). |
| `tracegraph.memory.jdbc.init-schema` | boolean | `true` | When `true`, calls `initSchema()` on startup to create the `tracegraph_memory` table if it does not exist. Set to `false` if you manage schema migrations yourself. |
| `tracegraph.memory.jdbc.table` | string | `tracegraph_memory` | Override the table name. Useful when the default conflicts with an existing schema. |

**Example:**

```yaml
tracegraph:
  memory:
    jdbc:
      enabled: true
      init-schema: false      # managed by Flyway
      table: tg_memory
```

---

## LLM Providers

`LlmAutoConfiguration` registers an `LlmClient` bean when `tracegraph-connectors` is on the classpath and `tracegraph.llm.provider` is set. It is guarded by `@ConditionalOnMissingBean(LlmClient)` — declare your own bean to override.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `tracegraph.llm.enabled` | boolean | `true` | Set to `false` to disable `LlmAutoConfiguration` entirely — no `LlmClient` bean is registered. |
| `tracegraph.llm.provider` | string | (none) | Required to activate an LLM bean. Accepted values: `openai`, `anthropic`. No bean is registered when unset. |

### OpenAI

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `tracegraph.llm.openai.api-key` | string | — | OpenAI API key. Required when `provider=openai`. |
| `tracegraph.llm.openai.model` | string | `gpt-4o-mini` | Model ID passed in every request. |
| `tracegraph.llm.openai.base-url` | string | `https://api.openai.com/v1` | Override for OpenAI-compatible endpoints (Azure, local proxies). |
| `tracegraph.llm.openai.timeout-seconds` | int | `30` | Request timeout for the underlying JDK `HttpClient`. |

### Anthropic

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `tracegraph.llm.anthropic.api-key` | string | — | Anthropic API key. Required when `provider=anthropic`. |
| `tracegraph.llm.anthropic.model` | string | `claude-3-5-haiku-20241022` | Model ID. |
| `tracegraph.llm.anthropic.anthropic-version` | string | `2023-06-01` | Value of the `anthropic-version` request header. |
| `tracegraph.llm.anthropic.timeout-seconds` | int | `60` | Request timeout. LLM calls can be slow — set conservatively. |

**Example — OpenAI:**

```yaml
tracegraph:
  llm:
    provider: openai
    openai:
      api-key: ${OPENAI_API_KEY}
      model: gpt-4o
      timeout-seconds: 45
```

**Example — Anthropic:**

```yaml
tracegraph:
  llm:
    provider: anthropic
    anthropic:
      api-key: ${ANTHROPIC_API_KEY}
      model: claude-3-5-sonnet-20241022
```

---

## Auto-configuration order

`MemoryAutoConfiguration` runs **before** `TraceGraphAutoConfiguration` so that a `JdbcMemoryStore` wins over the no-op default. `LlmAutoConfiguration` is independent.

`JdbcCheckpointStore` and `JdbcTraceStore` are **not** auto-wired because they require a user-supplied `Class<S>` — declare them as explicit `@Bean` definitions.
