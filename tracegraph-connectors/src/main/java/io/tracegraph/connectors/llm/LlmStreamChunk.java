package io.tracegraph.connectors.llm;

import java.util.Objects;

/**
 * A single chunk of a streaming LLM response.
 * <p>
 * Each chunk is either a content delta (token text), a tool-call delta (incremental
 * function-call name/arguments), or the terminal done marker.  Only one of these
 * is non-trivial in any given chunk:
 * <ul>
 *   <li>Content chunk – {@link #delta()} is non-empty, {@link #toolCallDelta()} is null.</li>
 *   <li>Tool-call delta chunk – {@link #toolCallDelta()} is non-null, {@link #delta()} is empty.</li>
 *   <li>Terminal chunk – {@link #finishReason()} is non-null; use {@link #isLast()} to detect.</li>
 * </ul>
 *
 * @param delta         incremental text content; may be empty but never null
 * @param finishReason  non-null only on the terminal chunk; indicates why the stream ended
 * @param toolCallDelta non-null when this chunk carries a streaming tool-call name/arg fragment
 */
public record LlmStreamChunk(String delta, LlmResponse.FinishReason finishReason,
                              ToolCallDelta toolCallDelta) {

    /**
     * Incremental fragment of a streaming tool call.
     *
     * @param index      zero-based index of the parallel tool call this delta belongs to
     * @param nameDelta  partial function name (non-null; may be empty after the first chunk)
     * @param argsDelta  partial JSON arguments string (non-null; may be empty on name-only chunks)
     */
    public record ToolCallDelta(int index, String nameDelta, String argsDelta) {
        public ToolCallDelta {
            Objects.requireNonNull(nameDelta, "nameDelta");
            Objects.requireNonNull(argsDelta, "argsDelta");
        }
    }

    public LlmStreamChunk {
        Objects.requireNonNull(delta, "delta");
    }

    /** Returns {@code true} if this is the terminal chunk. */
    public boolean isLast() {
        return finishReason != null;
    }

    /** Returns {@code true} if this chunk carries a streaming tool-call delta. */
    public boolean isToolCallDelta() {
        return toolCallDelta != null;
    }

    /** Create a content chunk. */
    public static LlmStreamChunk content(String delta) {
        return new LlmStreamChunk(delta, null, null);
    }

    /** Create the terminal chunk with no trailing content. */
    public static LlmStreamChunk done(LlmResponse.FinishReason reason) {
        return new LlmStreamChunk("", Objects.requireNonNull(reason, "reason"), null);
    }

    /** Create the terminal chunk with a final content delta. */
    public static LlmStreamChunk done(String delta, LlmResponse.FinishReason reason) {
        return new LlmStreamChunk(delta, Objects.requireNonNull(reason, "reason"), null);
    }

    /**
     * Create a tool-call delta chunk.  Providers that stream function calls emit
     * the name once and then arguments incrementally; callers accumulate all deltas
     * with the same {@code index} to reconstruct the full call.
     *
     * @param index      zero-based index of the parallel tool call
     * @param nameDelta  partial function name (may be empty for argument-only chunks)
     * @param argsDelta  partial JSON arguments (may be empty for name-only chunks)
     */
    public static LlmStreamChunk toolCallDelta(int index, String nameDelta, String argsDelta) {
        return new LlmStreamChunk("", null, new ToolCallDelta(index, nameDelta, argsDelta));
    }
}
