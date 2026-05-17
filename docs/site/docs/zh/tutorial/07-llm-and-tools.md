---
title: LLM 与工具
---

# 07 — LLM 与工具

TraceGraph 的连接器模块提供了供应商中立的 `LlmClient` 接口和 `ChatNode<S>` 适配器，可将任意 LLM 接入图节点。

## LlmClient 接口

```java
public interface LlmClient {
    LlmResponse complete(LlmRequest request);
    default Flow.Publisher<LlmStreamChunk> stream(LlmRequest request) { ... }
}
```

`LlmRequest` 和 `LlmResponse` 是 Record 类型，涵盖了各类聊天 API 的最小公分母：消息、模型、温度、最大令牌数、用量统计和结束原因。

## ChatNode

`ChatNode<S>` 将 `LlmClient` 与 `Node<S>` 桥接起来。您需要提供两个函数：

- `requestBuilder`：`(S state) -> LlmRequest` — 根据当前状态构建请求。
- `responseFolder`：`(S state, LlmResponse response) -> S` — 将响应折叠回状态中。

```java
record ChatState(String userMessage, String assistantReply, int promptTokens, int completionTokens) {}

LlmClient client = OpenAiLlmClient.builder()
    .apiKey(System.getenv("OPENAI_API_KEY"))
    .model("gpt-4o-mini")
    .build();

Node<ChatState> chatNode = ChatNode.<ChatState>builder()
    .client(client)
    .requestBuilder(state -> LlmRequest.builder()
        .message(ChatMessage.user(state.userMessage()))
        .model("gpt-4o-mini")
        .maxTokens(512)
        .build())
    .responseFolder((state, response) -> new ChatState(
        state.userMessage(),
        response.content(),
        response.usage().promptTokens(),
        response.usage().completionTokens()
    ))
    .build();
```

`ChatNode` 在每次成功响应后会自动调用 `ctx.reportUsage(promptTokens, completionTokens)`，因此令牌用量会显示在追踪步骤和 OTel Span 中。

## AnthropicLlmClient

```java
LlmClient anthropic = AnthropicLlmClient.builder()
    .apiKey(System.getenv("ANTHROPIC_API_KEY"))
    .model("claude-3-5-haiku-20241022")
    .build();
```

系统消息会被提升到 Anthropic Messages API 所需的顶级 `system` 字段。非 2xx 响应会以 `LlmHttpException(statusCode, body)` 的形式抛出。

## 流式传输

```java
Flow.Publisher<LlmStreamChunk> chunks = client.stream(request);
chunks.subscribe(new Flow.Subscriber<>() {
    public void onNext(LlmStreamChunk chunk) {
        System.out.print(chunk.delta());
        if (chunk.isLast()) System.out.println();
    }
    // ... 其他生命周期方法
});
```

不支持原生流式传输的提供商会回退到包装 `complete()` 结果的单块发布者。

## 要点总结

- `LlmClient` 是供应商中立的 SPI；`OpenAiLlmClient` 和 `AnthropicLlmClient` 是内置适配器。
- `ChatNode<S>` 通过 `requestBuilder` 和 `responseFolder` 将任意 `LlmClient` 适配为 `Node<S>`。
- 令牌用量通过 `ctx.reportUsage(...)` 自动上报，并显示在追踪记录和 Span 中。
- 非 2xx 响应以 `LlmHttpException` 形式抛出——可在重试策略或失败边中处理。
