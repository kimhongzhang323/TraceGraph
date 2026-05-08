---
title: 连接器（概念）
---

# 连接器

本页概述 TraceGraph 与外部系统（主要是大语言模型 LLM 和工具/向量存储）的连接层设计，包括核心抽象、失败语义、流式支持以及测试替代实现。

重要目标：
- 理解 `LlmClient` 的最小契约与流式扩展点。
- 学会如何在节点中使用 LLM 客户端并记录 token 使用情况。
- 理解网络/HTTP 错误如何被封装为 `LlmHttpException` 并在上层处理策略中使用（重试/降级）。

1) `LlmClient` 抽象

`LlmClient` 的设计目标是保持最小化和供应商中立：它只暴露用于完成请求的最小 API，以及可选的流式接口：

- `LlmResponse complete(LlmRequest req)` — 同步/批量完成（或包装为阻塞调用）。
- `Flow.Publisher<LlmStreamChunk> stream(LlmRequest req)` — 可选的增量流式接口（默认实现将 `complete()` 包装为单个 chunk）。

请求/响应模型用 record 表示（`LlmRequest`, `LlmResponse`, `ChatMessage`）。这使得不同适配器（OpenAI、Anthropic、自建服务）能够在不改变上层节点逻辑的情况下互换。

2) 流式与分块（streaming）

流式接口返回 `Flow.Publisher<LlmStreamChunk>`，每个 `LlmStreamChunk` 携带增量文本与是否终结的标志。上层 `ChatNode` 可以增量渲染响应或在接收首个有意义 chunk 后先行处理（低延迟体验）。如果提供方不支持流式，会由框架把 `complete()` 的结果封装成单个 chunk。

3) 错误与异常语义

网络或适配器错误会被封装为 `LlmHttpException`（包含 HTTP 状态码与响应体），以及更通用的 `LlmException` 分层。建议处理策略：

- 对于短暂性网络错误：应用幂等请求 ID + 指数退避重试。
- 对于 4xx（客户端）错误：通常不重试，除非是速率限制（429）并且服务表明可重试。
- 在节点层记录失败并把异常上抛到 `ExecutionResult`，以便 Trace/Replay 工具显示失败点。

4) 测试与模拟

`tracegraph-connectors` 提供 `MockLlmClient`（可配置为回放固定响应、延迟、或按脚本返回序列化输出），适用于单元测试与离线开发。把真实网络调用替换为 mock 能显著降低测试成本与 flakiness。

5) 代码片段：在节点中使用 `LlmClient`

```java
graph.node("chat", (state, ctx) -> {
	LlmRequest req = LlmRequest.ofSystemAndMessages(...);
	LlmResponse r = llmClient.complete(req);
	ctx.reportUsage(r.getPromptTokens(), r.getCompletionTokens());
	return state.withReply(r.getText());
});
```

6) 最佳实践

- 在配置中把超时与重试策略外放成可调参数（不要硬编码）。
- 对所有外部请求使用 `Context.idempotencyKey()` 来保证在恢复/重试时对端能去重。
- 把成本（token）与错误信息通过 `NodeListener`/`TraceRecorder` 上报，便于审计与预算控制。

练习：在 `examples/rag-agent` 中把 `OpenAiLlmClient` 替换为 `MockLlmClient`，并验证 `TraceRecorder` 正确记录了 `promptTokens` 与 `completionTokens`。
