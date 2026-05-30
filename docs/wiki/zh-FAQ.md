# 常见问题

> 🌐 English: **[[FAQ]]**

## TraceGraph 是 LangGraph 的克隆吗？

不是。它借鉴了图式运行时的思路空间，但刻意围绕 **Java 类型、显式运行时控制与 JVM 集成点**来设计。其主旨是面向 JVM 的生产级智能体运行时——类型化图、持久化记忆、深度可观测性——而非逐行移植。

## 不用 LLM 也能用吗？

可以。核心运行时**不依赖任何模型提供方**。它适用于任何类型化的工作流或编排图。LLM 支持通过 `tracegraph-connectors` 附加。

## 可以只配合 Spring Boot 使用吗？

可以，但 starter 是**附加性**的。底层你仍在使用同一个 `Graph<S>` 运行时与 SPI 抽象。见 **[[Spring Boot 集成|zh-Spring-Boot-Integration]]**。

## 今天就支持持久化恢复吗？

是的——通过 `CheckpointStore` SPI 与 `tracegraph-runtime`（`InMemoryCheckpointStore`、`JdbcCheckpointStore`）。持久化能力仍在演进，应视为 1.0 之前的基础设施。见 **[[运行时特性|zh-Runtime-Features]]**。

## 如何在不改图逻辑的情况下切换 LLM 提供方？

只需替换**一行**——`LlmClient` 的构造。`ChatNode`、`ReActAgent`、工具定义与你的状态类型都不变，因为 `LlmClient` 是唯一依赖：

```java
// OpenAI
LlmClient client = OpenAiLlmClient.builder()
    .apiKey(System.getenv("OPENAI_API_KEY")).model("gpt-4o").build();

// Anthropic —— 其余代码完全相同
LlmClient client = AnthropicLlmClient.builder()
    .apiKey(System.getenv("ANTHROPIC_API_KEY")).model("claude-3-5-sonnet-20241022").build();
```

## LLM 提供方返回非 2xx 状态会怎样？

两个 HTTP 适配器都抛出 **`LlmHttpException`**（含 `statusCode()` 与 `body()`）。它穿过 `ChatNode.apply()` 被视为节点失败。若节点配置了 `RetryPolicy`，执行器会重试整个调用——429 是指数退避重试的典型场景。见 **[[LLM 连接器|zh-LLM-Connectors]]**。

## 可以用本地模型（Ollama、LM Studio）吗？

可以。任何实现 OpenAI Chat Completions 规范的服务，都可通过自定义 `endpoint` 的 `OpenAiLlmClient` 使用：

```java
OpenAiLlmClient.builder()
    .apiKey("local")
    .endpoint("http://localhost:11434/v1")
    .model("llama3.1:8b")
    .build();
```

## `ChatNode` / `ReActAgent` 支持流式吗？

`ChatNode` 使用 `complete()` 而非 `stream()`。有**两种不同的流式**：

- **LLM token 流式**——`LlmClient.stream(request)` 返回增量 token，供 UI 使用。
- **图级事件流式**——`Graph.stream(initial)`（及 SSE 端点）发出 node enter/exit/retry/complete 事件。

见 **[[LLM 连接器|zh-LLM-Connectors]]** 与 **[[运行时特性|zh-Runtime-Features]]**。

## 为什么 `Node<S>` 是单参数而非 `Node<S, R>`？

这是刻意设计。子结果用**状态组合**——折叠进状态对象，而非穿一个第二类型参数。两个类型参数会让流式构建器的推断负担翻倍。这是项目的硬规则。

## 节点保证恰好运行一次吗？

不。节点在恢复时是**至少一次**。若节点执行中途崩溃，恢复时该节点从第 1 次尝试重跑。在有副作用的节点内用 `ctx.idempotencyKey()` 做去重，并保持**边谓词为纯函数**。见 **[[执行模型|zh-Execution-Model]]**。

## 检查点相对于边在何时写入？

**在节点退出之后、解析边之前。** 恢复时重新求值已保存 `lastCompletedNode` 的出边并继续。见 **[[运行时特性|zh-Runtime-Features]]**。

## 重试会产生额外的追踪步骤或 span 吗？

不会。重试是**同一 span 上的事件**（不是每次尝试一个 span），且**不产生额外追踪步骤**——`TraceStep.attempts` 记录次数。见 **[[可观测性与重放|zh-Observability-and-Replay]]**。

## 并行分支会出现在追踪/监听器中吗？

不会。`parallel(...)` 内的分支是匿名的——**无名称、无路径条目、无监听器事件、无 span**——且只产生**一个**追踪步骤。这是 Phase 2c 契约。见 **[[运行时特性|zh-Runtime-Features]]**。

## 工作记忆与 MemoryStore 有何区别？

**状态对象**是工作记忆（单次执行内）。**`MemoryStore`** 用于跨执行数据（会话、长期事实）。向量/语义检索在 `tracegraph-rag`，不在记忆 SPI。见 **[[记忆|zh-Memory]]** 与 **[[RAG 检索增强|zh-RAG]]**。

## 需要哪个 JDK？

**JDK 21。** 全程使用 record、模式匹配与虚拟线程。若 `mvn -version` 报告 Java 17 或更低，构建前更新 `JAVA_HOME`。

## API 稳定吗？

尚未——它处于 **1.0 之前**，次版本之间可能变更（每处变更都记录在 changelog）。`tracegraph-core` 是最稳妥的优先构建目标。发布产物才是真正的兼容性边界；连接器模块是集成辅助，而非提供方抽象的承诺。

## 如何贡献？

1. 使用 JDK 21 与 Maven 3.9+。
2. 提 PR 前运行 `mvn -B -ntp verify`。
3. 保持改动聚焦；行为变更时新增或更新测试。
4. 偏好小而可评审的 PR，而非大范围重构。

---

**还有问题？** 在[仓库](https://github.com/kimhongzhang323/TraceGraph/issues)提 issue。
