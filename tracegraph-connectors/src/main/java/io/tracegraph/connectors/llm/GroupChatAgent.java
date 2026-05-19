package io.tracegraph.connectors.llm;

import io.tracegraph.core.Graph;
import io.tracegraph.core.NodeResult;
import io.tracegraph.core.RoutingNode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Factory that produces a {@link Graph} implementing the group-chat multi-agent pattern
 * (AG2 {@code GroupChat}, AutoGen).
 *
 * <p>The graph has:
 * <ul>
 *   <li><b>speaker</b> — a {@link RoutingNode} that evaluates the termination predicate,
 *       then asks the configured {@link SpeakerSelector} for the next speaker</li>
 *   <li><b>one subgraph node per registered agent</b> — each runs its inner graph and
 *       then routes back to {@code speaker}</li>
 *   <li><b>done</b> — terminal pass-through</li>
 * </ul>
 *
 * <p>Termination: {@code speaker} routes to {@code done} when the
 * {@link Predicate#test(Object) terminationPredicate} returns {@code true}, or when the
 * selector returns {@code null}, {@code "done"}, or an unregistered name.
 *
 * @param <S> the state type shared by the group chat and every agent subgraph
 */
public final class GroupChatAgent<S> {

    private final Map<String, Graph<S>> agents;
    private final SpeakerSelector<S> speakerSelector;
    private final Predicate<S> terminationPredicate;
    private final int maxRounds;

    private GroupChatAgent(Builder<S> b) {
        this.agents = Collections.unmodifiableMap(new LinkedHashMap<>(b.agents));
        this.speakerSelector = b.speakerSelector;
        this.terminationPredicate = b.terminationPredicate;
        this.maxRounds = b.maxRounds;
    }

    public static <S> Builder<S> builder() {
        return new Builder<>();
    }

    /**
     * Build the group-chat graph.
     *
     * <p>The graph's {@code maxSteps} is derived from {@code maxRounds}: each round
     * consumes two steps (speaker + one agent invocation), plus one step for the final
     * "done" routing.
     */
    public Graph<S> buildGraph() {
        Graph.Builder<S> gb = Graph.<S>builder();

        gb.routingNode("speaker", speakerNode());
        for (Map.Entry<String, Graph<S>> e : agents.entrySet()) {
            gb.subgraph(e.getKey(), e.getValue());
            gb.edge(e.getKey(), "speaker");
        }
        gb.node("done", (state, ctx) -> state);
        gb.entry("speaker");
        gb.terminal("done");
        gb.maxSteps(maxRounds * 2 + 2);

        return gb.build();
    }

    private RoutingNode<S> speakerNode() {
        List<String> agentNames = List.copyOf(agents.keySet());
        return (state, ctx) -> {
            if (terminationPredicate.test(state)) {
                return NodeResult.goTo("done", state);
            }
            String selected = speakerSelector.next(state, agentNames);
            if (selected == null || selected.equals("done") || !agents.containsKey(selected)) {
                return NodeResult.goTo("done", state);
            }
            return NodeResult.goTo(selected, state);
        };
    }

    /** Fluent builder for {@link GroupChatAgent}. */
    public static final class Builder<S> {
        private final Map<String, Graph<S>> agents = new LinkedHashMap<>();
        private SpeakerSelector<S> speakerSelector;
        private Predicate<S> terminationPredicate;
        private int maxRounds = 10;

        private Builder() {}

        /**
         * Register a named agent subgraph. The name must not be {@code "speaker"} or
         * {@code "done"}, which are reserved for the group-chat's own nodes.
         *
         * @param name       unique agent name
         * @param agentGraph compiled graph to run as a subgraph step
         */
        public Builder<S> agent(String name, Graph<S> agentGraph) {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(agentGraph, "agentGraph");
            if (name.equals("speaker") || name.equals("done")) {
                throw new IllegalArgumentException(
                        "Reserved agent name: '" + name + "' (reserved: speaker, done)");
            }
            if (agents.containsKey(name)) {
                throw new IllegalArgumentException("Reserved or duplicate agent name: '" + name + "'");
            }
            agents.put(name, agentGraph);
            return this;
        }

        /** Selector that picks the next speaker on each round. */
        public Builder<S> speakerSelector(SpeakerSelector<S> selector) {
            this.speakerSelector = Objects.requireNonNull(selector, "speakerSelector");
            return this;
        }

        /**
         * Predicate evaluated at the top of every speaker round. When it returns
         * {@code true}, the graph routes straight to {@code "done"}.
         */
        public Builder<S> terminationPredicate(Predicate<S> predicate) {
            this.terminationPredicate = Objects.requireNonNull(predicate, "terminationPredicate");
            return this;
        }

        /**
         * Maximum number of speaker rounds before the graph halts.
         * Defaults to 10. The graph's {@code maxSteps} is {@code maxRounds * 2 + 2}.
         */
        public Builder<S> maxRounds(int rounds) {
            if (rounds <= 0) throw new IllegalArgumentException("maxRounds must be > 0");
            this.maxRounds = rounds;
            return this;
        }

        /** Validate and create the {@link GroupChatAgent}. */
        public GroupChatAgent<S> build() {
            Objects.requireNonNull(speakerSelector, "speakerSelector is required");
            Objects.requireNonNull(terminationPredicate, "terminationPredicate is required");
            if (agents.isEmpty()) throw new IllegalStateException("at least one agent must be registered");
            return new GroupChatAgent<>(this);
        }
    }
}
