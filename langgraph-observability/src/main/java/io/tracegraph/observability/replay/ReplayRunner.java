package io.tracegraph.observability.replay;

import io.tracegraph.core.ExecutionResult;
import io.tracegraph.core.Graph;
import io.tracegraph.core.spi.TraceRecorder;

import java.util.Objects;
import java.util.UUID;

/**
 * Re-executes a saved {@link ExecutionTrace} from a chosen step against a (possibly modified)
 * graph — the "what-if" side of replay. {@code reRunFrom(stepIndex)} seeds from
 * {@code parent.steps[stepIndex].before()}; {@code stepIndex == -1} replays from the graph entry
 * with {@link ExecutionTrace#initialState()}. An override seed is supported for hypotheticals.
 *
 * <p>Each fork gets a fresh executionId; the new trace records {@code forkedFromExecutionId} and
 * {@code forkedFromStepIndex} for lineage. No determinism guarantee — nodes own their own
 * determinism (LLM, HTTP, side effects). Construct via {@link #of(ExecutionTrace, Graph)}.
 *
 * @param <S> the graph's state type
 */
public final class ReplayRunner<S> {

    private final ExecutionTrace<S> parent;
    private final Graph<S> graph;

    private ReplayRunner(ExecutionTrace<S> parent, Graph<S> graph) {
        this.parent = Objects.requireNonNull(parent, "parent");
        this.graph = Objects.requireNonNull(graph, "graph");
    }

    public static <S> ReplayRunner<S> of(ExecutionTrace<S> parent, Graph<S> graph) {
        return new ReplayRunner<>(parent, graph);
    }

    public ExecutionResult<S> reRunFrom(int stepIndex) {
        S seed = stepIndex == -1 ? parent.initialState() : parent.steps().get(stepIndex).before();
        return reRunFromInternal(stepIndex, seed);
    }

    public ExecutionResult<S> reRunFrom(int stepIndex, S seedOverride) {
        return reRunFromInternal(stepIndex, seedOverride);
    }

    private ExecutionResult<S> reRunFromInternal(int stepIndex, S seed) {
        if (stepIndex < -1 || stepIndex >= parent.steps().size()) {
            throw new IndexOutOfBoundsException(
                    "stepIndex " + stepIndex + " out of range [-1, " + (parent.steps().size() - 1) + "]");
        }
        String startNode = stepIndex == -1 ? graph.entry() : parent.steps().get(stepIndex).nodeName();
        String newId = UUID.randomUUID().toString();

        TraceRecorder recorder = graph.traceRecorder();
        if (recorder instanceof RecordingTraceRecorder rec) {
            rec.stageForkLineage(newId, parent.executionId(), stepIndex);
        }

        return graph.runFrom(startNode, seed, newId);
    }
}
