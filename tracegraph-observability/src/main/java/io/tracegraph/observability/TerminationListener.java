package io.tracegraph.observability;

import io.tracegraph.core.spi.NodeListener;
import io.tracegraph.core.spi.TerminationSignalException;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * A {@link NodeListener} that halts the current execution cleanly when a user-supplied predicate
 * over a {@link TerminationContext} returns {@code true}. The predicate is evaluated on every
 * {@code onExit}; if it fires, the listener throws {@link TerminationSignalException} which the
 * executor surfaces as {@link io.tracegraph.core.Status#TERMINATED} with a {@code null} error.
 *
 * <p>Shared convergence contract for multi-agent factories ({@code SupervisorAgent},
 * {@code GroupChatAgent}, etc.) so they no longer need to reimplement their own
 * {@code maxRounds} / {@code maxIterations} counters on top of {@code Graph.maxSteps} (which
 * surfaces as the less semantically-precise {@link io.tracegraph.core.Status#HALTED}).
 *
 * <p>Built-in policies: {@link #maxTurns(int)}, {@link #afterNode(String)},
 * {@link #stateMatches(Predicate)}. Custom predicates are supplied via the constructor.
 *
 * <p>One listener instance per execution — the {@code nodesEnteredSoFar} counter is per-instance
 * and not reset between {@code Graph.run} calls. Thread-safe via {@link AtomicInteger}.
 *
 * @param <S> the graph's state type
 */
public final class TerminationListener<S> implements NodeListener {

    private final Predicate<TerminationContext<S>> predicate;
    private final AtomicInteger nodesEntered = new AtomicInteger();

    public TerminationListener(Predicate<TerminationContext<S>> predicate) {
        this.predicate = Objects.requireNonNull(predicate, "predicate");
    }

    /**
     * Terminates the execution when the total number of {@code onEnter} events observed by this
     * listener reaches {@code maxTurns}. Useful as a global convergence guard for supervisor /
     * group-chat loops, e.g. {@code maxTurns(10)} caps a router-agent loop at 5 rounds.
     */
    public static <S> TerminationListener<S> maxTurns(int maxTurns) {
        if (maxTurns <= 0) {
            throw new IllegalArgumentException("maxTurns must be positive, got: " + maxTurns);
        }
        return new TerminationListener<>(ctx -> ctx.nodesEnteredSoFar() >= maxTurns);
    }

    /**
     * Terminates the execution after the named node completes successfully.
     */
    public static <S> TerminationListener<S> afterNode(String nodeName) {
        Objects.requireNonNull(nodeName, "nodeName");
        if (nodeName.isBlank()) {
            throw new IllegalArgumentException("nodeName must not be blank");
        }
        return new TerminationListener<>(ctx -> nodeName.equals(ctx.nodeName()));
    }

    /**
     * Terminates the execution when the supplied state predicate matches the post-node state.
     */
    public static <S> TerminationListener<S> stateMatches(Predicate<S> statePredicate) {
        Objects.requireNonNull(statePredicate, "statePredicate");
        return new TerminationListener<>(ctx -> statePredicate.test(ctx.afterState()));
    }

    @Override
    public void onEnter(String nodeName, Object state) {
        nodesEntered.incrementAndGet();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onExit(String nodeName, Object state) {
        TerminationContext<S> ctx = new TerminationContext<>(nodeName, nodesEntered.get(), (S) state);
        if (predicate.test(ctx)) {
            throw new TerminationSignalException(
                    "Termination predicate fired at node '" + nodeName
                            + "' after " + ctx.nodesEnteredSoFar() + " node entries");
        }
    }
}
