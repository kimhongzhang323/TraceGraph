package io.tracegraph.connectors.llm;

import io.tracegraph.core.ExecutionResult;
import io.tracegraph.core.Graph;
import io.tracegraph.core.Merger;
import io.tracegraph.core.Node;
import io.tracegraph.core.Status;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Voting / consensus aggregator: fans {@link Graph#run(Object) run} across N named candidate
 * graphs concurrently, then folds the per-candidate states into a single winning state via a
 * {@link Tally}.
 *
 * <p>Modelled on the voting primitives in Semantic Kernel and AG2 reflection: every candidate
 * receives the same input state, runs its own graph to completion, and contributes a state to the
 * tally. Ships built-in tallies for the two common patterns:
 * <ul>
 *   <li>{@link Tally#majority(java.util.function.Function)} — pick the most-frequent answer</li>
 *   <li>{@link Tally#firstNonNull(java.util.function.Function)} — pick the first candidate that
 *       produced a non-null answer</li>
 * </ul>
 *
 * <p>The voter is itself a {@link Node} and is registered into the outer graph via
 * {@link Graph.Builder#node(String, Node) Builder.node(name, voter)}. Candidate names are used
 * only for diagnostics (and to enforce duplicate-name rejection at build time).
 *
 * <p>Candidate failures propagate as a node failure on the outer graph — the first candidate
 * (in declaration order) to fail wins, matching the semantics of
 * {@link Graph.Builder#parallel Graph.Builder.parallel(...)}.
 *
 * <p>Example:
 * <pre>{@code
 * Node<S> voter = VotingNode.<S>builder()
 *     .candidate("alice", aliceAgent)
 *     .candidate("bob",   bobAgent)
 *     .candidate("carol", carolAgent)
 *     .tally(Tally.majority(State::answer))
 *     .build();
 *
 * Graph<S> graph = Graph.<S>builder()
 *     .node("vote", voter)
 *     .entry("vote")
 *     .terminal("vote")
 *     .build();
 * }</pre>
 *
 * @param <S> the state type shared by the voter and every candidate graph
 */
public final class VotingNode<S> {

    private VotingNode() {}

    /** Start building a {@link VotingNode}. */
    public static <S> Builder<S> builder() {
        return new Builder<>();
    }

    /** Fluent builder for {@link VotingNode}. */
    public static final class Builder<S> {
        private final Map<String, Graph<S>> candidates = new LinkedHashMap<>();
        private Tally<S> tally;

        private Builder() {}

        /**
         * Register a named candidate graph. The graph receives the voter's input state and runs to
         * completion; the candidate's final state is passed to the {@link Tally}. Names must be
         * non-blank and unique within the voter.
         *
         * @param name           unique candidate name
         * @param candidateGraph compiled graph to run as a candidate
         * @return this builder
         */
        public Builder<S> candidate(String name, Graph<S> candidateGraph) {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(candidateGraph, "candidate graph");
            if (name.isBlank()) {
                throw new IllegalArgumentException("Candidate name must be non-blank");
            }
            if (candidates.containsKey(name)) {
                throw new IllegalArgumentException("duplicate candidate name: '" + name + "'");
            }
            candidates.put(name, candidateGraph);
            return this;
        }

        /**
         * Set the {@link Tally} that folds candidate states into the winning state.
         *
         * @param t tally function
         * @return this builder
         */
        public Builder<S> tally(Tally<S> t) {
            this.tally = Objects.requireNonNull(t, "tally");
            return this;
        }

        /**
         * Validate and create the voter as a {@link Node}.
         *
         * @return a node that runs every candidate in parallel and applies the tally
         * @throws NullPointerException  if {@code tally} was never set
         * @throws IllegalStateException if no candidates were registered
         */
        public Node<S> build() {
            Objects.requireNonNull(tally, "tally is required");
            if (candidates.isEmpty()) {
                throw new IllegalStateException("at least one candidate must be registered");
            }
            List<Graph<S>> candidateGraphs = List.copyOf(candidates.values());
            Tally<S> snapshot = tally;
            List<Node<S>> branches = new ArrayList<>(candidateGraphs.size());
            for (Graph<S> g : candidateGraphs) {
                branches.add((state, ctx) -> {
                    ExecutionResult<S> r = g.run(state);
                    if (r.status() != Status.COMPLETED) {
                        Throwable cause = r.error();
                        throw new RuntimeException(
                                "voter candidate failed (status=" + r.status() + ")", cause);
                    }
                    return r.finalState();
                });
            }
            Merger<S> merger = snapshot::apply;
            Graph<S> inner = Graph.<S>builder()
                    .parallel("vote", branches, merger)
                    .entry("vote")
                    .terminal("vote")
                    .build();
            return (state, ctx) -> {
                ExecutionResult<S> r = inner.run(state);
                if (r.status() != Status.COMPLETED) {
                    Throwable cause = r.error();
                    if (cause instanceof RuntimeException re) throw re;
                    if (cause instanceof Error e) throw e;
                    throw new RuntimeException("voter failed (status=" + r.status() + ")", cause);
                }
                return r.finalState();
            };
        }
    }
}
