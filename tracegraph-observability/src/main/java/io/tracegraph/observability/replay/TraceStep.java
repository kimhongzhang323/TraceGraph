package io.tracegraph.observability.replay;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * One node's contribution to an {@link ExecutionTrace}: the before/after state, total
 * {@link #attempts} (retries are folded in, not separate steps), wall-clock {@link #duration}, and
 * an {@link #error} if the node ultimately failed (in which case {@code after} is null and this is
 * the trace's last step).
 *
 * <p>{@link #rawInput} and {@link #rawOutput} are populated only when
 * {@code Graph.Builder.sensitiveDataLogging(true)} is set; otherwise both are {@code null}.
 *
 * @param <S> the graph's state type
 */
public record TraceStep<S>(int index, String nodeName, int attempts,
                           S before, S after, Duration duration, Throwable error,
                           List<TraceStep<S>> children, Usage usage,
                           String rawInput, String rawOutput) {

    public TraceStep {
        Objects.requireNonNull(nodeName, "nodeName");
        Objects.requireNonNull(duration, "duration");
        if (index < 0) throw new IllegalArgumentException("index must be >= 0");
        if (attempts < 1) throw new IllegalArgumentException("attempts must be >= 1");
        children = children == null ? List.of() : List.copyOf(children);
    }

    /** Backwards-compatible constructor without rawInput/rawOutput. */
    public TraceStep(int index, String nodeName, int attempts,
                     S before, S after, Duration duration, Throwable error,
                     List<TraceStep<S>> children, Usage usage) {
        this(index, nodeName, attempts, before, after, duration, error, children, usage, null, null);
    }

    /** Backwards-compatible constructor without usage. */
    public TraceStep(int index, String nodeName, int attempts,
                     S before, S after, Duration duration, Throwable error,
                     List<TraceStep<S>> children) {
        this(index, nodeName, attempts, before, after, duration, error, children, null, null, null);
    }

    public static <S> TraceStep<S> leaf(int index, String nodeName, int attempts,
                                        S before, S after, Duration duration, Throwable error) {
        return new TraceStep<>(index, nodeName, attempts, before, after, duration, error, List.of(), null, null, null);
    }

    /** Create a leaf step with token usage tracking. */
    public static <S> TraceStep<S> leaf(int index, String nodeName, int attempts,
                                        S before, S after, Duration duration, Throwable error,
                                        Usage usage) {
        return new TraceStep<>(index, nodeName, attempts, before, after, duration, error, List.of(), usage, null, null);
    }

    /** Create a leaf step with token usage tracking and raw I/O. */
    public static <S> TraceStep<S> leaf(int index, String nodeName, int attempts,
                                        S before, S after, Duration duration, Throwable error,
                                        Usage usage, String rawInput, String rawOutput) {
        return new TraceStep<>(index, nodeName, attempts, before, after, duration, error, List.of(), usage, rawInput, rawOutput);
    }

    public boolean failed() {
        return error != null;
    }

    /**
     * Token usage for an LLM step.
     *
     * @param promptTokens     input tokens consumed
     * @param completionTokens output tokens generated
     */
    public record Usage(int promptTokens, int completionTokens) {
        public Usage {
            if (promptTokens < 0) throw new IllegalArgumentException("promptTokens < 0");
            if (completionTokens < 0) throw new IllegalArgumentException("completionTokens < 0");
        }

        public int totalTokens() {
            return promptTokens + completionTokens;
        }

        public Usage plus(Usage other) {
            return new Usage(promptTokens + other.promptTokens, completionTokens + other.completionTokens);
        }

        public static final Usage ZERO = new Usage(0, 0);
    }
}
