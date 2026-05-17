package io.tracegraph.eval.golden;

import io.tracegraph.core.ExecutionResult;
import io.tracegraph.core.Graph;
import io.tracegraph.observability.replay.ExecutionTrace;
import io.tracegraph.observability.replay.TraceDiff;
import io.tracegraph.observability.replay.TraceStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Re-executes a {@link Graph} against each {@link GoldenTrace}'s {@linkplain GoldenTrace#seed()
 * initial state} and compares the outcome to the golden baseline.
 *
 * <p>Two factory modes:
 * <ul>
 *   <li>{@link #of(Graph, TraceStore)} — the graph carries a {@code RecordingTraceRecorder}
 *       writing to {@code runStore}; full {@link TraceDiff} is computed per case.</li>
 *   <li>{@link #of(Graph)} — no recorder; pass/fail is decided by final-state equality and
 *       status alone; {@link GoldenEvalResult#actual()} and {@link GoldenEvalResult#diff()}
 *       are null.</li>
 * </ul>
 *
 * @param <S> the graph's state type
 */
public final class EvalRunner<S> {

    private final Graph<S> graph;
    private final TraceStore runStore;

    private EvalRunner(Graph<S> graph, TraceStore runStore) {
        this.graph = graph;
        this.runStore = runStore;
    }

    /**
     * Create a runner that captures full execution traces for diffing.
     * The {@code graph} must be built with a {@code RecordingTraceRecorder} writing to
     * {@code runStore}; the runner reads traces back by executionId after each run.
     */
    public static <S> EvalRunner<S> of(Graph<S> graph, TraceStore runStore) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(runStore, "runStore");
        return new EvalRunner<>(graph, runStore);
    }

    /**
     * Create a runner without trace capture — pass/fail is decided by final-state equality.
     * {@link GoldenEvalResult#actual()} and {@link GoldenEvalResult#diff()} will be null.
     */
    public static <S> EvalRunner<S> of(Graph<S> graph) {
        Objects.requireNonNull(graph, "graph");
        return new EvalRunner<>(graph, null);
    }

    /**
     * Run the graph against each golden seed in sequence and return one result per golden.
     *
     * @param goldens ordered list of golden traces; must not be null
     * @return immutable result list in the same order as {@code goldens}
     */
    public List<GoldenEvalResult<S>> run(List<GoldenTrace<S>> goldens) {
        Objects.requireNonNull(goldens, "goldens");
        List<GoldenEvalResult<S>> results = new ArrayList<>(goldens.size());
        for (GoldenTrace<S> golden : goldens) {
            results.add(evaluate(golden));
        }
        return List.copyOf(results);
    }

    private GoldenEvalResult<S> evaluate(GoldenTrace<S> golden) {
        String runId = UUID.randomUUID().toString();
        try {
            ExecutionResult<S> result = graph.run(golden.seed(), runId);
            if (result.error() != null) {
                return new GoldenEvalResult<>(golden, null, null, false, result.error());
            }
            if (runStore != null) {
                return evaluateWithTrace(golden, runId, result);
            }
            return evaluateStatOnly(golden, result);
        } catch (Exception e) {
            return new GoldenEvalResult<>(golden, null, null, false, e);
        }
    }

    @SuppressWarnings("unchecked")
    private GoldenEvalResult<S> evaluateWithTrace(GoldenTrace<S> golden,
                                                  String runId,
                                                  ExecutionResult<S> result) {
        ExecutionTrace<S> actual = (ExecutionTrace<S>) runStore.load(runId).orElse(null);
        if (actual == null) {
            return evaluateStatOnly(golden, result);
        }
        TraceDiff<S> diff = TraceDiff.between(golden.trace(), actual);
        boolean passed = diff.sameFinalState() && diff.sameStatus();
        return new GoldenEvalResult<>(golden, actual, diff, passed, null);
    }

    private GoldenEvalResult<S> evaluateStatOnly(GoldenTrace<S> golden, ExecutionResult<S> result) {
        boolean passed = Objects.equals(result.finalState(), golden.trace().finalState())
                && result.status() == golden.trace().status();
        return new GoldenEvalResult<>(golden, null, null, passed, null);
    }
}
