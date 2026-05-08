---
title: LLM 与工具
---

# LLM 与工具


# LLM 与工具



TraceGraph 与 LLM 的接入是通过 `LlmClient` 抽象完成的（`complete()`、`stream()`）。`tracegraph-connectors` 提供 OpenAI / Anthropic 实现。将 LLM 封装进 `ChatNode` 是常见做法：

```java
graph.node("ask", ChatNode.of(requestBuilder, responseFolder));
```

关于工具（Tool）调用：

- Tool 是名为 `Tool.execute(String args) -> String` 的简单 SAM。ReAct 风格代理会在 LLM 响应中识别工具调用并执行。工具调用是有副作用的，务必设计幂等或用 `idempotencyKey()` 保护。

安全与成本注意事项：

- LLM 请求会产生成本，使用 `NodeListener.onUsage` 聚合令牌消耗。
- 对外部敏感操作（写入数据库、发送邮件）要加审计与人类确认（HITL）。

练习：把示例中的 `ChatNode` 配置为使用 `MockLlmClient`，观察本地调试输出而不消耗真实 API 配额。
