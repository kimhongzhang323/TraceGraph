---
title: 配置参考
---

# 配置参考 (Configuration Reference)

所有 TraceGraph 属性都使用 `tracegraph.*` 前缀。您可以在 `application.yml` 或 `application.properties` 中设置它们。

---

## Web / REST API

| 属性 (Property) | 类型 (Type) | 默认值 (Default) | 描述 (Description) |
|----------|------|---------|-------------|
| `tracegraph.web.enabled` | boolean | `true` | 当为 `false` 时，会抑制整个 `TraceWebAutoConfiguration` — 不会注册 `TraceController`、`TraceReplayController` 或 `TraceStreamController` bean。 |

**示例 — 完全禁用 REST API：**

```yaml
tracegraph:
  web:
    enabled: false
```

---

## Trace UI (可视化界面)

| 属性 (Property) | 类型 (Type) | 默认值 (Default) | 描述 (Description) |
|----------|------|---------|-------------|
| `tracegraph.ui.enabled` | boolean | `true` | 当为 `false` 时，会抑制 UI 自动配置。不提供 `/tracegraph/ui/*` 端点和静态资源。 |

---

## 内存 Memory (JDBC)

这些属性控制 `MemoryAutoConfiguration`。当 classpath 中同时存在 `tracegraph-memory`、`jackson-databind` 和 `DataSource` bean 时，它会注册一个 `JdbcMemoryStore`。

| 属性 (Property) | 类型 (Type) | 默认值 (Default) | 描述 (Description) |
|----------|------|---------|-------------|
| `tracegraph.memory.jdbc.enabled` | boolean | `true` | 设置为 `false` 以跳过 `JdbcMemoryStore` 的自动注册。将使用 `TraceGraphAutoConfiguration` 中的空操作 `MemoryStore` 代替（除非您声明了自己的 bean）。 |
| `tracegraph.memory.jdbc.init-schema` | boolean | `true` | 当为 `true` 时，在启动时调用 `initSchema()` 来创建 `tracegraph_memory` 表（如果该表不存在）。如果您自己管理架构迁移（如通过 Flyway），请将其设置为 `false`。 |
| `tracegraph.memory.jdbc.table` | string | `tracegraph_memory` | 覆盖表名。在默认表名与现有架构冲突时很有用。 |

**示例:**

```yaml
tracegraph:
  memory:
    jdbc:
      enabled: true
      init-schema: false      # 由 Flyway 管理
      table: tg_memory
```

---

## LLM 提供商 (LLM Providers)

`LlmAutoConfiguration` 在 classpath 中存在 `tracegraph-connectors` 且设置了 `tracegraph.llm.provider` 时注册一个 `LlmClient` bean。它受 `@ConditionalOnMissingBean(LlmClient)` 保护 — 声明您自己的 bean 即可覆盖它。

| 属性 (Property) | 类型 (Type) | 默认值 (Default) | 描述 (Description) |
|----------|------|---------|-------------|
| `tracegraph.llm.enabled` | boolean | `true` | 设置为 `false` 以完全禁用 `LlmAutoConfiguration` — 不会注册 `LlmClient` bean。 |
| `tracegraph.llm.provider` | string | (无) | 激活 LLM bean 的必填项。接受的值：`openai`、`anthropic`。如果未设置，则不会注册任何 bean。 |

### OpenAI

| 属性 (Property) | 类型 (Type) | 默认值 (Default) | 描述 (Description) |
|----------|------|---------|-------------|
| `tracegraph.llm.openai.api-key` | string | — | OpenAI API 密钥。当 `provider=openai` 时为必填项。 |
| `tracegraph.llm.openai.model` | string | `gpt-4o-mini` | 在每个请求中传递的模型 ID。 |
| `tracegraph.llm.openai.base-url` | string | `https://api.openai.com/v1` | 覆盖用于兼容 OpenAI 的端点（例如 Azure 或本地代理）。 |
| `tracegraph.llm.openai.timeout-seconds` | int | `30` | 底层 JDK `HttpClient` 的请求超时时间。 |

### Anthropic

| 属性 (Property) | 类型 (Type) | 默认值 (Default) | 描述 (Description) |
|----------|------|---------|-------------|
| `tracegraph.llm.anthropic.api-key` | string | — | Anthropic API 密钥。当 `provider=anthropic` 时为必填项。 |
| `tracegraph.llm.anthropic.model` | string | `claude-3-5-haiku-20241022` | 模型 ID。 |
| `tracegraph.llm.anthropic.anthropic-version` | string | `2023-06-01` | `anthropic-version` 请求头的值。 |
| `tracegraph.llm.anthropic.timeout-seconds` | int | `60` | 请求超时时间。LLM 调用可能很慢 — 请保守设置。 |

**示例 — OpenAI:**

```yaml
tracegraph:
  llm:
    provider: openai
    openai:
      api-key: ${OPENAI_API_KEY}
      model: gpt-4o
      timeout-seconds: 45
```

**示例 — Anthropic:**

```yaml
tracegraph:
  llm:
    provider: anthropic
    anthropic:
      api-key: ${ANTHROPIC_API_KEY}
      model: claude-3-5-sonnet-20241022
```

---

## 自动配置顺序

`MemoryAutoConfiguration` 在 `TraceGraphAutoConfiguration` **之前**运行，以便 `JdbcMemoryStore` 优先于默认的空操作内存存储。`LlmAutoConfiguration` 则是独立的。

`JdbcCheckpointStore` 和 `JdbcTraceStore` **不会**自动装配，因为它们需要用户提供的 `Class<S>` — 请将它们声明为显式的 `@Bean` 定义。
