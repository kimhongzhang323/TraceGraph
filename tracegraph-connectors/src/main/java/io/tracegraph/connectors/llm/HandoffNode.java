package io.tracegraph.connectors.llm;

import io.tracegraph.core.Graph;
import io.tracegraph.core.NodeResult;
import io.tracegraph.core.RoutingNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Factory that produces a {@link Graph} implementing peer-to-peer agent handoff (CrewAI delegation,
 * OpenAI Swarm pattern).
 *
 * <p>Unlike {@link SupervisorAgent}, there is no central router that gets re-entered between every
 * agent step. Instead, each registered target agent runs as a subgraph and is immediately followed
 * by a handoff routing node that makes a single LLM call to decide which peer agent should run
 * next — control flows directly from agent to agent without an intervening supervisor turn.
 *
 * <p>The handoff routing node:
 * <ol>
 *   <li>Builds an {@link LlmRequest} via {@code requestFactory}</li>
 *   <li>Calls the configured {@link LlmClient}</li>
 *   <li>Folds the response into state via {@code responseFolder}</li>
 *   <li>Reads the target name from state via {@code handoffSelector}</li>
 *   <li>Routes:
 *     <ul>
 *       <li>a registered target name &rarr; {@code goTo(target)}</li>
 *       <li>the reserved name {@code "continue"} &rarr; the next-in-declaration-order target,
 *           or {@code "done"} if the current agent is the last declared</li>
 *       <li>{@code null}, {@code "done"}, or any unknown name &rarr; {@code "done"}</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * @param <S> the state type shared by the handoff graph and all target agent subgraphs
 */
public final class HandoffNode<S> {

    private static final String DONE_NODE = "done";
    private static final String CONTINUE_TARGET = "continue";
    private static final String HANDOFF_NODE_PREFIX = "handoff-";

    private final LlmClient client;
    private final Map<String, Graph<S>> targets;
    private final Function<S, LlmRequest.Builder> requestFactory;
    private final BiFunction<S, LlmResponse, S> responseFolder;
    private final Function<S, String> handoffSelector;
    private final int maxHandoffs;

    private HandoffNode(Builder<S> b) {
        this.client = b.client;
        this.targets = new LinkedHashMap<>(b.targets);
        this.requestFactory = b.requestFactory;
        this.responseFolder = b.responseFolder;
        this.handoffSelector = b.handoffSelector;
        this.maxHandoffs = b.maxHandoffs;
    }

    public static <S> Builder<S> builder() {
        return new Builder<>();
    }

    /**
     * Build the handoff graph.
     *
     * <p>The graph's entry is the first declared target. Each target is wired as a subgraph
     * followed by its own handoff routing node. The graph's {@code maxSteps} budget accommodates
     * up to {@code maxHandoffs} agent-handoff cycles (each cycle consumes one agent step plus
     * one handoff routing step), plus one step for the terminal {@code "done"} node.
     */
    public Graph<S> buildGraph() {
        Graph.Builder<S> gb = Graph.<S>builder();
        List<String> names = new ArrayList<>(targets.keySet());

        for (int i = 0; i < names.size(); i++) {
            String agentName = names.get(i);
            String handoffName = HANDOFF_NODE_PREFIX + agentName;
            String continueTarget = (i + 1 < names.size()) ? names.get(i + 1) : DONE_NODE;
            gb.subgraph(agentName, targets.get(agentName));
            gb.edge(agentName, handoffName);
            gb.routingNode(handoffName, makeHandoffRouting(continueTarget));
        }
        gb.node(DONE_NODE, (state, ctx) -> state);
        gb.entry(names.get(0));
        gb.terminal(DONE_NODE);
        gb.maxSteps(maxHandoffs * 2 + 2);

        return gb.build();
    }

    private RoutingNode<S> makeHandoffRouting(String continueTarget) {
        return (state, ctx) -> {
            LlmRequest request = requestFactory.apply(state).build();
            LlmResponse response = client.complete(request);
            S newState = responseFolder.apply(state, response);
            String selected = handoffSelector.apply(newState);
            if (selected == null || selected.equals(DONE_NODE) || !isRoutable(selected)) {
                return NodeResult.goTo(DONE_NODE, newState);
            }
            if (selected.equals(CONTINUE_TARGET)) {
                return NodeResult.goTo(continueTarget, newState);
            }
            return NodeResult.goTo(selected, newState);
        };
    }

    private boolean isRoutable(String name) {
        return targets.containsKey(name) || name.equals(CONTINUE_TARGET);
    }

    /** Fluent builder for {@link HandoffNode}. */
    public static final class Builder<S> {
        private LlmClient client;
        private final Map<String, Graph<S>> targets = new LinkedHashMap<>();
        private Function<S, LlmRequest.Builder> requestFactory;
        private BiFunction<S, LlmResponse, S> responseFolder;
        private Function<S, String> handoffSelector;
        private int maxHandoffs = 10;

        private Builder() {}

        /** The LLM client used for each handoff decision. */
        public Builder<S> client(LlmClient client) {
            this.client = Objects.requireNonNull(client, "client");
            return this;
        }

        /**
         * Register a named target agent graph. The name must not be {@code "done"} or
         * {@code "continue"} (reserved for routing semantics).
         *
         * @param name       unique target name
         * @param agentGraph compiled graph to run as a subgraph step
         */
        public Builder<S> target(String name, Graph<S> agentGraph) {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(agentGraph, "agentGraph");
            if (name.equals(DONE_NODE) || name.equals(CONTINUE_TARGET)) {
                throw new IllegalArgumentException(
                        "Reserved target name: '" + name + "' (reserved: " + DONE_NODE + ", " + CONTINUE_TARGET + ")");
            }
            if (name.startsWith(HANDOFF_NODE_PREFIX)) {
                throw new IllegalArgumentException(
                        "Target name must not start with '" + HANDOFF_NODE_PREFIX + "': '" + name + "'");
            }
            if (targets.containsKey(name)) {
                throw new IllegalArgumentException("Reserved or duplicate target name: '" + name + "'");
            }
            targets.put(name, agentGraph);
            return this;
        }

        /**
         * Factory to build an {@link LlmRequest.Builder} from state for each handoff call.
         */
        public Builder<S> requestFactory(Function<S, LlmRequest.Builder> factory) {
            this.requestFactory = Objects.requireNonNull(factory, "requestFactory");
            return this;
        }

        /**
         * Fold the handoff LLM response into state. The folded state must subsequently let
         * {@code handoffSelector} read out the chosen target name.
         */
        public Builder<S> responseFolder(BiFunction<S, LlmResponse, S> folder) {
            this.responseFolder = Objects.requireNonNull(folder, "responseFolder");
            return this;
        }

        /**
         * Extract the chosen handoff target from state. Return a registered target name to
         * dispatch, the literal {@code "continue"} to fall through to the next-declared target,
         * {@code "done"} or {@code null} to terminate, or any unknown name to terminate.
         */
        public Builder<S> handoffSelector(Function<S, String> selector) {
            this.handoffSelector = Objects.requireNonNull(selector, "handoffSelector");
            return this;
        }

        /**
         * Maximum number of handoffs before the graph halts. Defaults to 10.
         * The graph's {@code maxSteps} is {@code maxHandoffs * 2 + 2}.
         */
        public Builder<S> maxHandoffs(int handoffs) {
            if (handoffs <= 0) throw new IllegalArgumentException("maxHandoffs must be > 0");
            this.maxHandoffs = handoffs;
            return this;
        }

        /** Validate and create the {@link HandoffNode}. */
        public HandoffNode<S> build() {
            Objects.requireNonNull(client, "client is required");
            Objects.requireNonNull(requestFactory, "requestFactory is required");
            Objects.requireNonNull(responseFolder, "responseFolder is required");
            Objects.requireNonNull(handoffSelector, "handoffSelector is required");
            if (targets.isEmpty()) throw new IllegalStateException("at least one target must be registered");
            return new HandoffNode<>(this);
        }
    }
}
