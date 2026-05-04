package io.tracegraph.connectors.llm;

import java.util.Objects;

/**
 * A single chunk of a streaming LLM response.
 * <p>
 * Each chunk carries a content delta (token fragment) and an optional
 * {@link LlmResponse.FinishReason} that is non-null only on the final chunk.
 *
 * @param delta        the incremental text content in this chunk; may be empty but never null
 * @param finishReason non-null only on the terminal chunk; indicates why the stream ended
 */
public record LlmStreamChunk(String delta, LlmResponse.FinishReason finishReason) {

    public LlmStreamChunk {
        Objects.requireNonNull(delta, "delta");
    }

    /** Returns {@code true} if this is the terminal chunk. */
    public boolean isLast() {
        return finishReason != null;
    }

    /** Create a content chunk. */
    public static LlmStreamChunk content(String delta) {
        return new LlmStreamChunk(delta, null);
    }

    /** Create the terminal chunk. */
    public static LlmStreamChunk done(LlmResponse.FinishReason reason) {
        return new LlmStreamChunk("", Objects.requireNonNull(reason, "reason"));
    }

    /** Create the terminal chunk with a final content delta. */
    public static LlmStreamChunk done(String delta, LlmResponse.FinishReason reason) {
        return new LlmStreamChunk(delta, Objects.requireNonNull(reason, "reason"));
    }
}
