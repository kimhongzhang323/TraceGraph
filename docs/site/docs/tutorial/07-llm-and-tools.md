# 07 — LLM & Tools

TraceGraph's connector module provides a vendor-neutral `LlmClient` interface and `ChatNode<S>` adapter that wires any LLM into a graph node.

## LlmClient interface

```java
public interface LlmClient {
    LlmResponse complete(LlmRequest request);
    default Flow.Publisher<LlmStreamChunk> stream(LlmRequest request) { ... }
}
```

`LlmRequest` and `LlmResponse` are records covering the common denominator across chat APIs: messages, model, temperature, max tokens, usage, and finish reason.

## ChatNode

`ChatNode<S>` bridges `LlmClient` and `Node<S>`. You supply two functions:

- `requestBuilder`: `(S state) -> LlmRequest` — build the request from the current state.
- `responseFolder`: `(S state, LlmResponse response) -> S` — fold the response back into the state.

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

`ChatNode` automatically calls `ctx.reportUsage(promptTokens, completionTokens)` after each successful response, so token usage appears in trace steps and OTel spans.

## AnthropicLlmClient

```java
LlmClient anthropic = AnthropicLlmClient.builder()
    .apiKey(System.getenv("ANTHROPIC_API_KEY"))
    .model("claude-3-5-haiku-20241022")
    .build();
```

System messages are lifted into the top-level `system` field required by the Anthropic Messages API. Non-2xx responses surface as `LlmHttpException(statusCode, body)`.

## Streaming

```java
Flow.Publisher<LlmStreamChunk> chunks = client.stream(request);
chunks.subscribe(new Flow.Subscriber<>() {
    public void onNext(LlmStreamChunk chunk) {
        System.out.print(chunk.delta());
        if (chunk.isLast()) System.out.println();
    }
    // ... other lifecycle methods
});
```

Providers without native streaming support fall back to a single-chunk publisher wrapping `complete()`.

## Key takeaways

- `LlmClient` is the vendor-neutral SPI; `OpenAiLlmClient` and `AnthropicLlmClient` are bundled adapters.
- `ChatNode<S>` adapts any `LlmClient` into a `Node<S>` via `requestBuilder` and `responseFolder`.
- Token usage is reported automatically through `ctx.reportUsage(...)` and appears in traces and spans.
- Non-2xx responses surface as `LlmHttpException` — handle them in a retry policy or a failure edge.
