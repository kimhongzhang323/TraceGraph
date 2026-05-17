package io.tracegraph.core.spi;

/**
 * Provider-neutral metadata for a single LLM call performed inside a node. Surfaced to
 * {@link NodeListener#onLlmCall(String, LlmCallInfo)} so listeners can emit standard
 * GenAI semantic-convention attributes (see
 * <a href="https://opentelemetry.io/docs/specs/semconv/gen-ai/">OTel GenAI semantic conventions</a>)
 * or compute per-provider cost.
 *
 * <p>{@code system} is the provider identifier (e.g. {@code "openai"}, {@code "anthropic"},
 * {@code "mock"}); may be {@code null} when unknown. {@code model} is the provider model id
 * (e.g. {@code "gpt-4o"}); may be {@code null}. {@code finishReason} is the lowercased terminal
 * reason (e.g. {@code "stop"}, {@code "length"}, {@code "tool_calls"}); may be {@code null}.
 * Token counts are non-negative.
 */
public record LlmCallInfo(String system, String model, int promptTokens, int completionTokens,
                          String finishReason) {

    public LlmCallInfo {
        if (promptTokens < 0) throw new IllegalArgumentException("promptTokens < 0");
        if (completionTokens < 0) throw new IllegalArgumentException("completionTokens < 0");
    }
}
