package io.tracegraph.observability.replay;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
<<<<<<< HEAD
 * A single step in a recorded execution trace.
 *
 * @param index     zero-based step index
 * @param nodeName  the node that executed
 * @param attempts  number of attempts (including retries)
 * @param before    state before node execution
 * @param after     state after node execution
 * @param duration  wall-clock duration of execution
 * @param error     error if the step failed; null otherwise
 * @param children  sub-steps (e.g. for parallel branches)
 * @param usage     optional LLM token usage for this step; null if not an LLM node
=======
 * One node's contribution to an {@link ExecutionTrace}: the before/after state, total
 * {@link #attempts} (retries are folded in, not separate steps), wall-clock {@link #duration}, and
 * an {@link #error} if the node ultimately failed (in which case {@code after} is null and this is
 * the trace's last step).
 *
 * @param <S> the graph's state type
>>>>>>> 55f9606bb806c3028941d338ba0cb5632dc396b9
 */
public record TraceStep<S>(int index, String nodeName, int attempts,
                           S before, S after, Duration duration, Throwable error,
                           List<TraceStep<S>> children, Usage usage) {

    public TraceStep {
        Objects.requireNonNull(nodeName, "nodeName");
        Objects.requireNonNull(duration, "duration");
        if (index < 0) throw new IllegalArgumentException("index must be >= 0");
        if (attempts < 1) throw new IllegalArgumentException("attempts must be >= 1");
        children = children == null ? List.of() : List.copyOf(children);
    }

    /** Backwards-compatible constructor without usage. */
    public TraceStep(int index, String nodeName, int attempts,
                     S before, S after, Duration duration, Throwable error,
                     List<TraceStep<S>> children) {
        this(index, nodeName, attempts, before, after, duration, error, children, null);
    }

    public static <S> TraceStep<S> leaf(int index, String nodeName, int attempts,
                                        S before, S after, Duration duration, Throwable error) {
        return new TraceStep<>(index, nodeName, attempts, before, after, duration, error, List.of(), null);
    }

    /** Create a leaf step with token usage tracking. */
    public static <S> TraceStep<S> leaf(int index, String nodeName, int attempts,
                                        S before, S after, Duration duration, Throwable error,
                                        Usage usage) {
        return new TraceStep<>(index, nodeName, attempts, before, after, duration, error, List.of(), usage);
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
