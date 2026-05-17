package io.tracegraph.observability.replay;

import io.tracegraph.core.Status;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

/**
 * {@link TraceStore} decorator that drops {@link #save(ExecutionTrace)} calls rejected by a
 * {@link SampleStrategy}. Reads ({@link #load}, {@link #listIds}, {@link #delete}) pass through
 * to the delegate unchanged — sampling controls write volume only.
 *
 * <p>Use to bound disk/network/DB cost for high-throughput graphs. Wire via
 * {@code Graph.Builder.traceRecorder(new RecordingTraceRecorder(new SamplingTraceStore(delegate, strategy)))}.
 *
 * <p>Built-in strategies: {@link #random(double)}, {@link #slowExecutions(Duration)},
 * {@link #failedOnly()}. Compose strategies with {@code SampleStrategy.or}/{@code and}.
 */
public final class SamplingTraceStore implements TraceStore {

    private final TraceStore delegate;
    private final SampleStrategy strategy;

    public SamplingTraceStore(TraceStore delegate, SampleStrategy strategy) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.strategy = Objects.requireNonNull(strategy, "strategy");
    }

    @Override
    public void save(ExecutionTrace<?> trace) {
        Objects.requireNonNull(trace, "trace");
        if (strategy.sample(trace)) {
            delegate.save(trace);
        }
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

    /**
     * Sampling decision for one finished trace. Returning {@code true} forwards the trace to the
     * delegate store; {@code false} drops it.
     */
    @FunctionalInterface
    public interface SampleStrategy {

        boolean sample(ExecutionTrace<?> trace);

        default SampleStrategy and(SampleStrategy other) {
            Objects.requireNonNull(other, "other");
            return t -> sample(t) && other.sample(t);
        }

        default SampleStrategy or(SampleStrategy other) {
            Objects.requireNonNull(other, "other");
            return t -> sample(t) || other.sample(t);
        }
    }

    /**
     * Keep each trace with probability {@code rate}. {@code rate == 0} drops everything;
     * {@code rate == 1} keeps everything. Uses {@link ThreadLocalRandom} so it is virtual-thread
     * friendly and lock-free under contention.
     *
     * @throws IllegalArgumentException if {@code rate} is NaN or outside {@code [0.0, 1.0]}
     */
    public static SampleStrategy random(double rate) {
        if (Double.isNaN(rate) || rate < 0.0 || rate > 1.0) {
            throw new IllegalArgumentException("rate must be in [0.0, 1.0], got " + rate);
        }
        return randomWith(rate, () -> ThreadLocalRandom.current().nextDouble());
    }

    static SampleStrategy randomWith(double rate, DoubleSupplier rng) {
        Objects.requireNonNull(rng, "rng");
        if (rate <= 0.0) return t -> false;
        if (rate >= 1.0) return t -> true;
        return t -> rng.getAsDouble() < rate;
    }

    /**
     * Keep traces whose wall-clock duration ({@code completedAt - startedAt}) is at least
     * {@code threshold}. Useful for capturing only outliers without recording the typical case.
     *
     * @throws NullPointerException     if {@code threshold} is null
     * @throws IllegalArgumentException if {@code threshold} is negative
     */
    public static SampleStrategy slowExecutions(Duration threshold) {
        Objects.requireNonNull(threshold, "threshold");
        if (threshold.isNegative()) {
            throw new IllegalArgumentException("threshold must be >= 0");
        }
        return t -> Duration.between(t.startedAt(), t.completedAt()).compareTo(threshold) >= 0;
    }

    /** Keep only traces with {@link Status#FAILED}. */
    public static SampleStrategy failedOnly() {
        return t -> t.status() == Status.FAILED;
    }
}
