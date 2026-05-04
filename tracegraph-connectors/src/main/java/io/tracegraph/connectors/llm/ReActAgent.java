package io.tracegraph.connectors.llm;

import io.tracegraph.core.Context;
import io.tracegraph.core.Graph;
import io.tracegraph.core.Node;
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
 * Factory that produces a {@link Graph} implementing the ReAct (Reason + Act) agent loop.
 * <p>
 * The graph has three nodes:
 * <ol>
 *   <li><b>llm</b> — calls the LLM with the current messages + tool definitions</li>
 *   <li><b>tools</b> — executes all tool calls from the LLM response and appends results</li>
 *   <li><b>done</b> — terminal node that passes state through</li>
 * </ol>
 * The routing logic after "llm" checks if the response contains tool calls:
 * if yes → route to "tools"; otherwise → route to "done".
 * After "tools", always route back to "llm" for the next reasoning step.
 *
 * @param <S> the state type — must carry the conversation messages
 */
public final class ReActAgent<S> {

    private final LlmClient client;
    private final Map<String, Tool> tools;
    private final List<ToolDefinition> toolDefinitions;
    private final Function<S, LlmRequest.Builder> requestFactory;
    private final BiFunction<S, LlmResponse, S> responseFolder;
    private final BiFunction<S, List<ToolResult>, S> toolResultFolder;
    private final int maxIterations;

    private ReActAgent(Builder<S> b) {
        this.client = b.client;
        this.tools = Map.copyOf(b.tools);
        this.toolDefinitions = List.copyOf(b.toolDefinitions);
        this.requestFactory = b.requestFactory;
        this.responseFolder = b.responseFolder;
        this.toolResultFolder = b.toolResultFolder;
        this.maxIterations = b.maxIterations;
    }

    public static <S> Builder<S> builder() {
        return new Builder<>();
    }

    /**
     * Build the ReAct agent graph.
     * <p>
     * The graph can be further configured (listeners, checkpoint store, etc.)
     * by the caller via the returned builder's parent-builder pattern, or used directly.
     */
    public Graph<S> buildGraph() {
        return Graph.<S>builder()
                .routingNode("llm", llmNode())
                .node("tools", toolsNode())
                .node("done", (state, ctx) -> state)
                .entry("llm")
                .terminal("done")
                .edge("tools", "llm")
                .maxSteps(maxIterations * 2 + 1)
                .build();
    }

    private RoutingNode<S> llmNode() {
        return (state, ctx) -> {
            LlmRequest request = requestFactory.apply(state)
                    .tools(toolDefinitions)
                    .build();
            LlmResponse response = client.complete(request);
            S newState = responseFolder.apply(state, response);

            if (response.hasToolCalls()) {
                return NodeResult.goTo("tools", newState);
            }
            return NodeResult.goTo("done", newState);
        };
    }

    private Node<S> toolsNode() {
        return (state, ctx) -> {
            // Extract the latest tool calls from the state.
            // The responseFolder should have stored the LlmResponse's tool calls
            // into the state. We re-derive them by calling the request factory
            // to get the latest messages and checking the last assistant message.
            List<ToolResult> results = executeToolCalls(state);
            return toolResultFolder.apply(state, results);
        };
    }

    private List<ToolResult> executeToolCalls(S state) {
        // Build request to inspect messages — the last assistant message should have tool calls
        LlmRequest req = requestFactory.apply(state).tools(toolDefinitions).build();
        List<ChatMessage> messages = req.messages();

        // Find the last assistant message with tool calls
        List<ToolCall> pendingCalls = List.of();
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage m = messages.get(i);
            if (m.role() == ChatMessage.Role.ASSISTANT && !m.toolCalls().isEmpty()) {
                pendingCalls = m.toolCalls();
                break;
            }
        }

        List<ToolResult> results = new ArrayList<>(pendingCalls.size());
        for (ToolCall call : pendingCalls) {
            Tool tool = tools.get(call.name());
            if (tool == null) {
                results.add(ToolResult.error(call.id(),
                        "Unknown tool: '" + call.name() + "'. Available: " + tools.keySet()));
                continue;
            }
            try {
                String output = tool.execute(call.arguments());
                results.add(ToolResult.success(call.id(), output));
            } catch (Exception e) {
                results.add(ToolResult.error(call.id(),
                        e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        }
        return results;
    }

    public static final class Builder<S> {
        private LlmClient client;
        private final Map<String, Tool> tools = new LinkedHashMap<>();
        private final List<ToolDefinition> toolDefinitions = new ArrayList<>();
        private Function<S, LlmRequest.Builder> requestFactory;
        private BiFunction<S, LlmResponse, S> responseFolder;
        private BiFunction<S, List<ToolResult>, S> toolResultFolder;
        private int maxIterations = 10;

        private Builder() {}

        /** The LLM client to use. */
        public Builder<S> client(LlmClient client) {
            this.client = Objects.requireNonNull(client, "client");
            return this;
        }

        /**
         * Register a tool with its definition.
         *
         * @param definition metadata describing the tool for the LLM
         * @param tool       the implementation
         */
        public Builder<S> tool(ToolDefinition definition, Tool tool) {
            Objects.requireNonNull(definition, "definition");
            Objects.requireNonNull(tool, "tool");
            this.tools.put(definition.name(), tool);
            this.toolDefinitions.add(definition);
            return this;
        }

        /**
         * Factory to build an {@link LlmRequest.Builder} from state.
         * The builder's {@code tools} will be set automatically; the caller must set
         * {@code messages}, {@code model}, etc.
         */
        public Builder<S> requestFactory(Function<S, LlmRequest.Builder> factory) {
            this.requestFactory = Objects.requireNonNull(factory, "requestFactory");
            return this;
        }

        /**
         * Fold an LLM response into the state.
         * This should append the assistant message (including any tool calls) to the
         * conversation history in the state.
         */
        public Builder<S> responseFolder(BiFunction<S, LlmResponse, S> folder) {
            this.responseFolder = Objects.requireNonNull(folder, "responseFolder");
            return this;
        }

        /**
         * Fold tool results into the state.
         * This should append tool result messages to the conversation history.
         */
        public Builder<S> toolResultFolder(BiFunction<S, List<ToolResult>, S> folder) {
            this.toolResultFolder = Objects.requireNonNull(folder, "toolResultFolder");
            return this;
        }

        /**
         * Maximum number of LLM↔tool round-trips before the graph halts.
         * Defaults to 10. The graph's maxSteps is set to {@code maxIterations * 2 + 1}.
         */
        public Builder<S> maxIterations(int max) {
            if (max <= 0) throw new IllegalArgumentException("maxIterations must be > 0");
            this.maxIterations = max;
            return this;
        }

        public ReActAgent<S> build() {
            Objects.requireNonNull(client, "client is required");
            Objects.requireNonNull(requestFactory, "requestFactory is required");
            Objects.requireNonNull(responseFolder, "responseFolder is required");
            Objects.requireNonNull(toolResultFolder, "toolResultFolder is required");
            if (tools.isEmpty()) throw new IllegalStateException("at least one tool must be registered");
            return new ReActAgent<>(this);
        }
    }
}
