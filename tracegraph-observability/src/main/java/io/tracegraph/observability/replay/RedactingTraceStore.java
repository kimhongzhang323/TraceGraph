package io.tracegraph.observability.replay;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Decorates a {@link TraceStore}, applying a {@link Redactor} to raw LLM I/O and error messages
 * before traces reach the delegate. State snapshots are typed {@code S} and pass through
 * untouched — redact state content via your own state design, or disable
 * {@code sensitiveDataLogging} entirely.
 *
 * <p>Error throwables whose message changes under redaction are replaced by a
 * {@code RuntimeException("[originalClassName] redactedMessage")}, mirroring the lossy
 * round-trip convention of {@link JsonFileTraceStore}.
 */
public final class RedactingTraceStore implements TraceStore {

    private final TraceStore delegate;
    private final Redactor redactor;

    public RedactingTraceStore(TraceStore delegate, Redactor redactor) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.redactor = Objects.requireNonNull(redactor, "redactor");
    }

    @Override
    public void save(ExecutionTrace<?> trace) {
        delegate.save(redactTrace(trace));
    }

    @Override
    public Optional<ExecutionTrace<?>> load(String executionId) {
        return delegate.load(executionId);
    }

    @Override
    public void delete(String executionId) {
        delegate.delete(executionId);
    }

    @Override
    public List<String> listIds() {
        return delegate.listIds();
    }

    private <S> ExecutionTrace<S> redactTrace(ExecutionTrace<S> trace) {
        List<TraceStep<S>> steps = new ArrayList<>(trace.steps().size());
        for (TraceStep<S> step : trace.steps()) {
            steps.add(redactStep(step));
        }
        return new ExecutionTrace<>(trace.executionId(), trace.initialState(), trace.finalState(),
                trace.status(), redactError(trace.error()), steps,
                trace.startedAt(), trace.completedAt(),
                trace.forkedFromExecutionId(), trace.forkedFromStepIndex(),
                trace.parentExecutionId(), trace.parentStepIndex(), trace.correlationId());
    }

    private <S> TraceStep<S> redactStep(TraceStep<S> step) {
        List<TraceStep<S>> children = new ArrayList<>(step.children().size());
        for (TraceStep<S> child : step.children()) {
            children.add(redactStep(child));
        }
        return new TraceStep<>(step.index(), step.nodeName(), step.attempts(),
                step.before(), step.after(), step.duration(), redactError(step.error()),
                children, step.usage(),
                redactor.redact(step.rawInput()), redactor.redact(step.rawOutput()));
    }

    private Throwable redactError(Throwable error) {
        if (error == null || error.getMessage() == null) return error;
        String redacted = redactor.redact(error.getMessage());
        if (redacted.equals(error.getMessage())) return error;
        return new RuntimeException("[" + error.getClass().getName() + "] " + redacted);
    }
}
