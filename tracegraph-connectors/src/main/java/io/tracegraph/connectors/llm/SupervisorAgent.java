package io.tracegraph.connectors.llm;

import io.tracegraph.core.Graph;
import io.tracegraph.core.NodeResult;
import io.tracegraph.core.RoutingNode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Factory that produces a {@link Graph} implementing the supervisor multi-agent pattern.
 *
 * <p>The supervisor graph has three kinds of nodes:
 * <ol>
 *   <li><b>router</b> — a {@link RoutingNode} that calls the router LLM and dispatches
 *       to a named sub-agent or terminates</li>
 *   <li><b>one subgraph node per registered agent</b> — each runs its inner graph to completion
 *       as a single step, then returns to the router</li>
 *   <li><b>done</b> — terminal node</li>
 * </ol>
 *
 * <p>The router LLM response is folded into state via {@code responseFolder}; the
 * {@code agentSelector} then extracts the routing decision. If the selector returns
 * {@code null}, {@code "done"}, or an unrecognised name, execution terminates.
 *
 * @param <S> the state type shared by the supervisor and all sub-agent graphs
 */
public final class SupervisorAgent<S> {

    private final LlmClient routerClient;
    private final Map<String, Graph<S>> agents;
    private final Function<S, LlmRequest.Builder> requestFactory;
    private final BiFunction<S, LlmResponse, S> responseFolder;
    private final Function<S, String> agentSelector;
    private final int maxRounds;

    private SupervisorAgent(Builder<S> b) {
        this.routerClient = b.routerClient;
        this.agents = Map.copyOf(b.agents);
        this.requestFactory = b.requestFactory;
        this.responseFolder = b.responseFolder;
        this.agentSelector = b.agentSelector;
        this.maxRounds = b.maxRounds;
    }

    public static <S> Builder<S> builder() {
        return new Builder<>();
    }

    /**
     * Build the supervisor graph.
     *
     * <p>The graph's {@code maxSteps} is derived from {@code maxRounds}: each round consumes
     * two steps (router + one agent invocation), plus one step for the final "done" routing.
     */
    public Graph<S> buildGraph() {
        Graph.Builder<S> gb = Graph.<S>builder();

        gb.routingNode("router", routerNode());
        for (Map.Entry<String, Graph<S>> e : agents.entrySet()) {
            gb.subgraph(e.getKey(), e.getValue());
            gb.edge(e.getKey(), "router");
        }
        gb.node("done", (state, ctx) -> state);
        gb.entry("router");
        gb.terminal("done");
        gb.maxSteps(maxRounds * 2 + 2);

        return gb.build();
    }

    private RoutingNode<S> routerNode() {
        return (state, ctx) -> {
            LlmRequest request = requestFactory.apply(state).build();
            LlmResponse response = routerClient.complete(request);
            S newState = responseFolder.apply(state, response);
            String selected = agentSelector.apply(newState);
            if (selected == null || selected.equals("done") || !agents.containsKey(selected)) {
                return NodeResult.goTo("done", newState);
            }
            return NodeResult.goTo(selected, newState);
        };
    }

    /** Fluent builder for {@link SupervisorAgent}. */
    public static final class Builder<S> {
        private LlmClient routerClient;
        private final Map<String, Graph<S>> agents = new LinkedHashMap<>();
        private Function<S, LlmRequest.Builder> requestFactory;
        private BiFunction<S, LlmResponse, S> responseFolder;
        private Function<S, String> agentSelector;
        private int maxRounds = 10;

        private Builder() {}

        /** The LLM client used by the router to select the next sub-agent. */
        public Builder<S> routerClient(LlmClient client) {
            this.routerClient = Objects.requireNonNull(client, "routerClient");
            return this;
        }

        /**
         * Register a named sub-agent graph. The name must not be {@code "router"} or
         * {@code "done"}, which are reserved for the supervisor's own nodes.
         *
         * @param name       unique sub-agent name
         * @param agentGraph compiled graph to run as a subgraph step
         */
        public Builder<S> agent(String name, Graph<S> agentGraph) {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(agentGraph, "agentGraph");
            if (name.equals("router") || name.equals("done")) {
                throw new IllegalArgumentException(
                        "Reserved agent name: '" + name + "' (reserved: router, done)");
            }
            if (agents.containsKey(name)) {
                throw new IllegalArgumentException("Reserved or duplicate agent name: '" + name + "'");
            }
            agents.put(name, agentGraph);
            return this;
        }

        /**
         * Factory to build an {@link LlmRequest.Builder} from state for each router call.
         * The caller sets messages, model, etc.; tool lists are not injected automatically.
         */
        public Builder<S> requestFactory(Function<S, LlmRequest.Builder> factory) {
            this.requestFactory = Objects.requireNonNull(factory, "requestFactory");
            return this;
        }

        /**
         * Fold the router LLM response into state. This is where the chosen agent name
         * (returned by the LLM) is typically stored so that {@code agentSelector} can read it.
         */
        public Builder<S> responseFolder(BiFunction<S, LlmResponse, S> folder) {
            this.responseFolder = Objects.requireNonNull(folder, "responseFolder");
            return this;
        }

        /**
         * Extract the router's decision from state. Return {@code "done"}, {@code null},
         * or an unregistered name to terminate; return a registered agent name to dispatch.
         */
        public Builder<S> agentSelector(Function<S, String> selector) {
            this.agentSelector = Objects.requireNonNull(selector, "agentSelector");
            return this;
        }

        /**
         * Maximum number of agent-dispatch rounds before the graph halts.
         * Defaults to 10. The graph's {@code maxSteps} is {@code maxRounds * 2 + 2}.
         */
        public Builder<S> maxRounds(int rounds) {
            if (rounds <= 0) throw new IllegalArgumentException("maxRounds must be > 0");
            this.maxRounds = rounds;
            return this;
        }

        /** Validate and create the {@link SupervisorAgent}. */
        public SupervisorAgent<S> build() {
            Objects.requireNonNull(routerClient, "routerClient is required");
            Objects.requireNonNull(requestFactory, "requestFactory is required");
            Objects.requireNonNull(responseFolder, "responseFolder is required");
            Objects.requireNonNull(agentSelector, "agentSelector is required");
            if (agents.isEmpty()) throw new IllegalStateException("at least one agent must be registered");
            return new SupervisorAgent<>(this);
        }
    }
}
