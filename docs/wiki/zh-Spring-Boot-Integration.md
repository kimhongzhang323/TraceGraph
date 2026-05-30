# Spring Boot 集成

`tracegraph-spring-boot-starter` 提供自动配置、REST 端点与依赖注入。它仅依赖 `spring-boot-autoconfigure`；observability、connectors 与 web 部分是**可选**的，按条件激活。

> 🌐 English: **[[Spring Boot Integration]]**
>
> Spring Boot 在父 BOM 中固定为 **3.3.5**。Spring 导入仅限本模块——core 保持无框架。

## 自动配置了什么

| 自动配置 | 注册 | 守卫 |
|---|---|---|
| `TraceGraphAutoConfiguration` | `NodeListener`、`CheckpointStore`、`TraceRecorder`、`MemoryStore` 的 no-op bean | 每个 `@ConditionalOnMissingBean` |
| `TraceWebAutoConfiguration`（`boot.web`） | `TraceController`、`TraceReplayController`、`TraceStreamController` | web 应用 + 存在 `TraceStore` + `tracegraph.web.enabled` |
| `MemoryAutoConfiguration`（`boot.memory`） | `JdbcMemoryStore` | 存在 `DataSource` + Jackson |
| `LlmAutoConfiguration`（`boot.llm`） | `OpenAiLlmClient` / `AnthropicLlmClient` | 类路径有 `tracegraph-connectors` + 设了 provider |
| `EmbeddingAutoConfiguration` | 嵌入客户端 | 设了 `tracegraph.rag.embedding.provider` |
| `A2AAutoConfiguration` | `InMemoryAgentBus`、`A2AController` | 存在 `tracegraph-a2a` |

每个 SPI bean 都由 `@ConditionalOnMissingBean` 守卫，因此**你的 bean 总是胜出**。

## 由你定义 Graph

`Graph<?>` **不**自动注册——它在 `S` 上泛型，需你自行声明 bean 并注入 SPI bean：

```java
@Configuration
public class GraphConfig {
    @Bean
    public Graph<AgentState> agentGraph(LlmClient llmClient, MemoryStore memoryStore, TraceRecorder traceRecorder) {
        ChatNode<AgentState> chat = new ChatNode<>(
                llmClient,
                state -> new LlmRequest("gpt-4o", state.messages(), 0.7, 1024),
                (state, resp) -> state.withAnswer(resp.content()));
        return Graph.<AgentState>builder()
                .node("chat", chat).entry("chat").terminal("chat")
                .memoryStore(memoryStore).traceRecorder(traceRecorder)
                .build();
    }
}
```

## 配置属性

全部属性使用 `tracegraph.*` 前缀，置于 `application.yml` 或 `application.properties`。

### Web / REST API

| 属性 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `tracegraph.web.enabled` | boolean | `true` | `false` 抑制**全部** web 自动配置。 |
| `tracegraph.ui.enabled` | boolean | `true` | `false` 抑制 UI 自动配置与 `/tracegraph/ui/*`。 |

### 记忆（JDBC）

| 属性 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `tracegraph.memory.jdbc.enabled` | boolean | `true` | `false` 跳过 `JdbcMemoryStore` 自动注册。 |
| `tracegraph.memory.jdbc.init-schema` | boolean | `true` | 启动时调用 `initSchema()`；自管迁移则设 `false`。 |
| `tracegraph.memory.jdbc.table` | string | `tracegraph_memory` | 覆盖表名。 |

`MemoryAutoConfiguration` 在 `TraceGraphAutoConfiguration` **之前**运行，使 JDBC 存储胜过 no-op 默认。

### LLM 提供方

仅当设了 `tracegraph.llm.provider` 时 `LlmAutoConfiguration` 才注册 `LlmClient`。由 `@ConditionalOnMissingBean(LlmClient)` 守卫。

| 属性 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `tracegraph.llm.enabled` | boolean | `true` | `false` 完全禁用 LLM 自动配置。 |
| `tracegraph.llm.provider` | string | （无） | `openai` 或 `anthropic`。未设则无 bean。 |
| `tracegraph.llm.openai.api-key` | string | — | `provider=openai` 时必填 |
| `tracegraph.llm.openai.model` | string | `gpt-4o-mini` | |
| `tracegraph.llm.openai.base-url` | string | `https://api.openai.com/v1` | 覆盖以适配兼容端点 |
| `tracegraph.llm.openai.timeout-seconds` | int | `30` | |
| `tracegraph.llm.anthropic.api-key` | string | — | `provider=anthropic` 时必填 |
| `tracegraph.llm.anthropic.model` | string | `claude-3-5-haiku-20241022` | |
| `tracegraph.llm.anthropic.anthropic-version` | string | `2023-06-01` | 请求头值 |
| `tracegraph.llm.anthropic.timeout-seconds` | int | `60` | LLM 调用可能慢——保守设置 |

### 示例

```yaml
tracegraph:
  web:
    enabled: true
  memory:
    jdbc:
      enabled: true
      init-schema: false      # 由 Flyway 管理
      table: tg_memory
  llm:
    provider: openai
    openai:
      api-key: ${OPENAI_API_KEY}
      model: gpt-4o
      timeout-seconds: 45
```

## 刻意不自动接线

`JdbcCheckpointStore` 与 `JdbcTraceStore` **不**自动接线，因为它们需要用户提供的 `Class<S>`。请显式声明 `@Bean`：

```java
@Bean
TraceStore traceStore(DataSource ds) {
    JdbcTraceStore<AgentState> store = JdbcTraceStore.of(ds, AgentState.class);
    store.initSchema();
    return store;
}
```

## REST 端点

当存在 observability 模块与 `TraceStore` bean 时，starter 在 `/tracegraph/*` 暴露追踪、重放、恢复与流式端点。完整参考：**[[REST API 参考|zh-REST-API-Reference]]**。

快速列表：

- `GET /tracegraph/traces`（分页；`X-Total-Count` 头）
- `GET /tracegraph/traces/{id}`
- `GET /tracegraph/traces/{a}/diff/{b}`
- `DELETE /tracegraph/traces/{id}`
- `POST /tracegraph/traces/{id}/replay?step=N`
- `POST /tracegraph/traces/{id}/resume`
- `POST /tracegraph/traces/stream`（SSE）

replay/resume/stream 控制器要求**单个** `Graph<?>` bean（`@ConditionalOnSingleCandidate(Graph.class)`）。

## 可运行示例

```bash
mvn -f examples/spring-boot-app/pom.xml spring-boot:run
```

---

**相关：** **[[REST API 参考|zh-REST-API-Reference]]** · **[[记忆|zh-Memory]]** · **[[LLM 连接器|zh-LLM-Connectors]]** · **[[可观测性与重放|zh-Observability-and-Replay]]**
