package io.tracegraph.connectors.llm;

/**
 * Vendor-neutral interface for chat-completion LLM calls. Takes an {@link LlmRequest} and returns
 * an {@link LlmResponse}; covers the lowest common denominator across chat-LLM APIs (messages,
 * model, temperature, maxTokens; usage tokens; finish reason).
 *
 * <p>Ships with {@code OpenAiLlmClient} (OpenAI-compatible endpoints) and {@code AnthropicLlmClient}
 * (Anthropic Messages API), plus {@code MockLlmClient} for tests. Adapt to a graph node via
 * {@code ChatNode}. Streaming and embeddings are deferred slices.
 */
@FunctionalInterface
public interface LlmClient {
    LlmResponse complete(LlmRequest request);
}
