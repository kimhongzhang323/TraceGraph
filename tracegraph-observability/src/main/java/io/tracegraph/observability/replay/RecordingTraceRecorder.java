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
    private final ConcurrentMap<String, ParentLineage> pendingParent = new ConcurrentHashMap<>();

    @Override
    public void recordUsage(String executionId, String nodeName, int promptTokens, int completionTokens) {
        Builder b = active.get(executionId);
        if (b == null) return;
        b.pendingUsage.merge(nodeName,
                new TraceStep.Usage(promptTokens, completionTokens), TraceStep.Usage::plus);
    }

    @Override
    public void recordRawIO(String executionId, String nodeName, String rawInput, String rawOutput) {
        Builder b = active.get(executionId);
        if (b == null) return;
        b.pendingRawInput.put(nodeName, rawInput);
        b.pendingRawOutput.put(nodeName, rawOutput);
    }

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
        TraceStep.Usage usage = b.pendingUsage.remove(nodeName);
        String rawInput = b.pendingRawInput.remove(nodeName);
        String rawOutput = b.pendingRawOutput.remove(nodeName);
        b.steps.add(TraceStep.leaf(b.steps.size(), nodeName, attempts, before, after,
                Duration.ofNanos(durationNanos), null, usage, rawInput, rawOutput));
    }

    @Override
    public void recordExitWithChildren(String executionId, String nodeName, int attempts,
                                       Object before, Object after, long durationNanos, List<?> childrenSteps) {
        Builder b = active.get(executionId);
        if (b == null) return;
        TraceStep.Usage usage = b.pendingUsage.remove(nodeName);
        String rawInput = b.pendingRawInput.remove(nodeName);
        String rawOutput = b.pendingRawOutput.remove(nodeName);
        @SuppressWarnings("unchecked")
        List<TraceStep<Object>> children = (List<TraceStep<Object>>) childrenSteps;
        b.steps.add(new TraceStep<>(b.steps.size(), nodeName, attempts, before, after,
                Duration.ofNanos(durationNanos), null, children, usage, rawInput, rawOutput));
    }

    @Override
    public void recordError(String executionId, String nodeName, Throwable error) {
        Builder b = active.get(executionId);
        if (b == null) return;
        b.steps.add(TraceStep.leaf(b.steps.size(), nodeName, 1, null, null, Duration.ZERO, error));
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
        ParentLineage parent = pendingParent.remove(executionId);
        String parentId = parent == null ? null : parent.parentExecutionId();
        int parentIdx = parent == null ? -1 : parent.parentStepIndex();
        ExecutionTrace<?> trace = new ExecutionTrace(
                b.executionId, b.initialState, finalState,
                status, b.error,
                List.copyOf(b.steps),
                b.startedAt, Instant.now(),
                forkedFromId, forkedFromIdx,
                parentId, parentIdx);
        store.save(trace);
    }

    @Override
    public void recordChildOf(String childExecutionId, String parentExecutionId, int parentStepIndex) {
        if (parentExecutionId == null) return;
        pendingParent.put(childExecutionId, new ParentLineage(parentExecutionId, parentStepIndex));
    }

    void stageForkLineage(String executionId, String parentExecutionId, int parentStepIndex) {
        pendingLineage.put(executionId, new ForkLineage(parentExecutionId, parentStepIndex));
    }

    private record ForkLineage(String parentExecutionId, int parentStepIndex) {}

    private record ParentLineage(String parentExecutionId, int parentStepIndex) {}

    private static final class Builder {
        final String executionId;
        final Object initialState;
        final Instant startedAt;
        final List<TraceStep<?>> steps = new ArrayList<>();
        final ConcurrentMap<String, TraceStep.Usage> pendingUsage = new ConcurrentHashMap<>();
        final ConcurrentMap<String, String> pendingRawInput = new ConcurrentHashMap<>();
        final ConcurrentMap<String, String> pendingRawOutput = new ConcurrentHashMap<>();
        Throwable error;

        Builder(String executionId, Object initialState, Instant startedAt) {
            this.executionId = executionId;
            this.initialState = initialState;
            this.startedAt = startedAt;
        }
    }
}
