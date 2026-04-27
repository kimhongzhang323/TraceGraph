package io.tracegraph.observability.replay;

import io.tracegraph.core.Status;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class Replayer<S> {

    private final ExecutionTrace<S> trace;

    private Replayer(ExecutionTrace<S> trace) {
        this.trace = trace;
    }

    public static <S> Replayer<S> of(ExecutionTrace<S> trace) {
        return new Replayer<>(Objects.requireNonNull(trace, "trace"));
    }

    public String executionId() { return trace.executionId(); }

    public Status status() { return trace.status(); }

    public Throwable error() { return trace.error(); }

    public S initialState() { return trace.initialState(); }

    public S finalState() { return trace.finalState(); }

    public int stepCount() { return trace.steps().size(); }

    public List<TraceStep<S>> steps() { return trace.steps(); }

    public TraceStep<S> stepAt(int index) { return trace.steps().get(index); }

    public List<String> pathTaken() {
        List<String> names = new ArrayList<>(trace.steps().size());
        for (TraceStep<S> step : trace.steps()) names.add(step.nodeName());
        return List.copyOf(names);
    }

    public S stateAt(int index) {
        if (index < -1 || index >= trace.steps().size()) {
            throw new IndexOutOfBoundsException("step index " + index);
        }
        if (index == -1) return trace.initialState();
        TraceStep<S> step = trace.steps().get(index);
        return step.after();
    }
}
