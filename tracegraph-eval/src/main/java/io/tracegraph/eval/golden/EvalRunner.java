package io.tracegraph.eval.golden;

import io.tracegraph.core.ExecutionResult;
import io.tracegraph.core.Graph;
import io.tracegraph.observability.replay.ExecutionTrace;
import io.tracegraph.observability.replay.TraceDiff;
import io.tracegraph.observability.replay.TraceStep;
import io.tracegraph.observability.replay.TraceStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Re-executes a {@link Graph} against each {@link GoldenTrace}'s {@linkplain GoldenTrace#seed()
 * initial state} and compares the outcome to the golden baseline.
 *
 * <p>Three construction modes:
 * <ul>
 *   <li>{@link #of(Graph, TraceStore)} — the graph carries a {@code RecordingTraceRecorder}
 *       writing to {@code runStore}; full {@link TraceDiff} is computed per case.</li>
 *   <li>{@link #of(Graph)} — no recorder; pass/fail is decided by final-state equality and
 *       status alone; {@link GoldenEvalResult#actual()} and {@link GoldenEvalResult#diff()}
 *       are null.</li>
 *   <li>{@link #builder()} — same as the factories plus per-step {@link TraceAssertion}s that
 *       inspect intermediate trace steps (tool args, retry counts, retrieved docs); requires a
 *       wired {@code runStore} to fire.</li>
 * </ul>
 *
 * @param <S> the graph's state type
 */
public final class EvalRunner<S> {

    private final Graph<S> graph;
    private final TraceStore runStore;
    private final Map<String, List<TraceAssertion<S>>> assertions;

    private EvalRunner(Graph<S> graph, TraceStore runStore,
                       Map<String, List<TraceAssertion<S>>> assertions) {
        this.graph = graph;
        this.runStore = runStore;
        this.assertions = assertions;
    }

    /**
     * Create a runner that captures full execution traces for diffing.
     * The {@code graph} must be built with a {@code RecordingTraceRecorder} writing to
     * {@code runStore}; the runner reads traces back by executionId after each run.
     */
    public static <S> EvalRunner<S> of(Graph<S> graph, TraceStore runStore) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(runStore, "runStore");
        return new EvalRunner<>(graph, runStore, Map.of());
    }

    /**
     * Create a runner without trace capture — pass/fail is decided by final-state equality.
     * {@link GoldenEvalResult#actual()} and {@link GoldenEvalResult#diff()} will be null.
     */
    public static <S> EvalRunner<S> of(Graph<S> graph) {
        Objects.requireNonNull(graph, "graph");
        return new EvalRunner<>(graph, null, Map.of());
    }

    /** Start a fluent builder for runners that need per-step trace assertions. */
    public static <S> Builder<S> builder() {
        return new Builder<>();
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
                return new GoldenEvalResult<>(golden, null, null, false, result.error(), List.of());
            }
            if (runStore != null) {
                return evaluateWithTrace(golden, runId, result);
            }
            return evaluateStatOnly(golden, result);
        } catch (Exception e) {
            return new GoldenEvalResult<>(golden, null, null, false, e, List.of());
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
        List<AssertionFailure> assertionFailures = runAssertions(actual);
        boolean passed = diff.sameFinalState() && diff.sameStatus() && assertionFailures.isEmpty();
        return new GoldenEvalResult<>(golden, actual, diff, passed, null, assertionFailures);
    }

    private GoldenEvalResult<S> evaluateStatOnly(GoldenTrace<S> golden, ExecutionResult<S> result) {
        boolean passed = Objects.equals(result.finalState(), golden.trace().finalState())
                && result.status() == golden.trace().status();
        return new GoldenEvalResult<>(golden, null, null, passed, null, List.of());
    }

    private List<AssertionFailure> runAssertions(ExecutionTrace<S> actual) {
        if (assertions.isEmpty()) {
            return List.of();
        }
        List<AssertionFailure> failures = new ArrayList<>();
        for (TraceStep<S> step : actual.steps()) {
            List<TraceAssertion<S>> forNode = assertions.get(step.nodeName());
            if (forNode == null) {
                continue;
            }
            for (TraceAssertion<S> assertion : forNode) {
                Optional<String> message = assertion.check(step);
                if (message.isPresent()) {
                    failures.add(new AssertionFailure(step.nodeName(), step.index(), message.get()));
                }
            }
        }
        return List.copyOf(failures);
    }

    /**
     * Fluent builder for {@link EvalRunner}. Not thread-safe; construct on a single thread.
     *
     * @param <S> the graph's state type
     */
    public static final class Builder<S> {

        private Graph<S> graph;
        private TraceStore runStore;
        private final Map<String, List<TraceAssertion<S>>> assertions = new HashMap<>();

        private Builder() {
        }

        public Builder<S> graph(Graph<S> graph) {
            this.graph = Objects.requireNonNull(graph, "graph");
            return this;
        }

        public Builder<S> runStore(TraceStore runStore) {
            this.runStore = Objects.requireNonNull(runStore, "runStore");
            return this;
        }

        /**
         * Register an assertion to run against each top-level trace step whose {@code nodeName}
         * matches. Multiple assertions on the same node fire in registration order.
         *
         * <p>Assertions only fire when a {@code runStore} is wired — without one, the runner
         * has no captured trace to inspect.
         */
        public Builder<S> assertion(String nodeName, TraceAssertion<S> assertion) {
            Objects.requireNonNull(nodeName, "nodeName");
            Objects.requireNonNull(assertion, "assertion");
            assertions.computeIfAbsent(nodeName, k -> new ArrayList<>()).add(assertion);
            return this;
        }

        public EvalRunner<S> build() {
            Objects.requireNonNull(graph, "graph");
            Map<String, List<TraceAssertion<S>>> frozen = new HashMap<>();
            for (Map.Entry<String, List<TraceAssertion<S>>> entry : assertions.entrySet()) {
                frozen.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
            return new EvalRunner<>(graph, runStore, Map.copyOf(frozen));
        }
    }
}
