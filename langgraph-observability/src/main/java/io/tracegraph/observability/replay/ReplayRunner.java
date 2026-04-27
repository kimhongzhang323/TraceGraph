package io.tracegraph.observability.replay;

import io.tracegraph.core.ExecutionResult;
import io.tracegraph.core.Graph;
import io.tracegraph.core.spi.TraceRecorder;

import java.util.Objects;
import java.util.UUID;

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
