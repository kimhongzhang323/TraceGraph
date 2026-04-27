package io.tracegraph.observability.replay;

import io.tracegraph.core.Status;
import io.tracegraph.core.spi.TraceRecorder;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class RecordingTraceRecorder implements TraceRecorder {

    private final TraceStore store;
    private final ConcurrentMap<String, Builder> active = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ForkLineage> pendingLineage = new ConcurrentHashMap<>();

    public RecordingTraceRecorder(TraceStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    @SuppressWarnings("unchecked")
    public void recordStart(String executionId, Object initialState) {
        Builder b = new Builder(executionId, initialState, Instant.now());
        if (!pendingLineage.containsKey(executionId)) {
            store.load(executionId).ifPresent(prior -> b.steps.addAll((List) prior.steps()));
        }
        active.put(executionId, b);
    }

    @Override
    public void recordEnter(String executionId, String nodeName, int attempt, Object state) {}

    @Override
    public void recordRetry(String executionId, String nodeName, int attempt, Throwable error) {}

    @Override
    public void recordExit(String executionId, String nodeName, int attempts,
                           Object before, Object after, long durationNanos) {
        Builder b = active.get(executionId);
        if (b == null) return;
        b.steps.add(new TraceStep<>(b.steps.size(), nodeName, attempts, before, after,
                Duration.ofNanos(durationNanos), null));
    }

    @Override
    public void recordError(String executionId, String nodeName, Throwable error) {
        Builder b = active.get(executionId);
        if (b == null) return;
        b.steps.add(new TraceStep<>(b.steps.size(), nodeName, 1, null, null, Duration.ZERO, error));
        b.error = error;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void recordComplete(String executionId, Status status, Object finalState) {
        Builder b = active.remove(executionId);
        if (b == null) return;
        ForkLineage lineage = pendingLineage.remove(executionId);
        String forkedFromId = lineage == null ? null : lineage.parentExecutionId();
        int forkedFromIdx = lineage == null ? -1 : lineage.parentStepIndex();
        ExecutionTrace<?> trace = new ExecutionTrace(
                b.executionId, b.initialState, finalState,
                status, b.error,
                List.copyOf(b.steps),
                b.startedAt, Instant.now(),
                forkedFromId, forkedFromIdx);
        store.save(trace);
    }

    void stageForkLineage(String executionId, String parentExecutionId, int parentStepIndex) {
        pendingLineage.put(executionId, new ForkLineage(parentExecutionId, parentStepIndex));
    }

    private record ForkLineage(String parentExecutionId, int parentStepIndex) {}

    private static final class Builder {
        final String executionId;
        final Object initialState;
        final Instant startedAt;
        final List<TraceStep<?>> steps = new ArrayList<>();
        Throwable error;

        Builder(String executionId, Object initialState, Instant startedAt) {
            this.executionId = executionId;
            this.initialState = initialState;
            this.startedAt = startedAt;
        }
    }
}
