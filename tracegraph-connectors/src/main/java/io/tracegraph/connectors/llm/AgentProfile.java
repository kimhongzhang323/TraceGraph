package io.tracegraph.connectors.llm;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Identity, role prompt, and tool set for a single agent in a multi-agent graph.
 *
 * <p>A profile binds together everything that distinguishes one agent from another:
 * a name, a system prompt establishing the agent's role, the tools it is allowed to
 * invoke, and an optional {@code memoryScope} for partitioning shared memory.
 *
 * <p>Use {@link ReActAgent.Builder#profile(AgentProfile)} to apply a profile when
 * constructing an agent — it overrides any prior {@code tool(...)} registrations
 * so each agent in a swarm sees only its own tool set.
 *
 * @param <S> the state type of the graph the agent belongs to
 * @param name              the agent's identity (must not be blank)
 * @param systemPrompt      role prompt prepended as a {@link ChatMessage.Role#SYSTEM} message
 *                          on every outgoing LLM request; empty string disables injection
 * @param tools             implementations, parallel to {@code toolDefinitions}
 * @param toolDefinitions   LLM-visible metadata, parallel to {@code tools}
 * @param memoryScope       optional state-to-state function for scoping memory access;
 *                          {@code null} when this agent does not partition memory
 */
public record AgentProfile<S>(String name,
                              String systemPrompt,
                              List<Tool> tools,
                              List<ToolDefinition> toolDefinitions,
                              Function<S, S> memoryScope) {

    public AgentProfile {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        Objects.requireNonNull(systemPrompt, "systemPrompt");
        tools = tools == null ? List.of() : List.copyOf(tools);
        toolDefinitions = toolDefinitions == null ? List.of() : List.copyOf(toolDefinitions);
        if (tools.size() != toolDefinitions.size()) {
            throw new IllegalArgumentException(
                    "tools and toolDefinitions must have the same length; got "
                            + tools.size() + " tools and " + toolDefinitions.size() + " definitions");
        }
    }

    /**
     * Start a fluent builder for a new agent profile.
     *
     * @param <S> the state type of the agent's graph
     * @return a new builder
     */
    public static <S> Builder<S> builder() {
        return new Builder<>();
    }

    /**
     * Fluent builder for {@link AgentProfile}.
     *
     * @param <S> the state type of the agent's graph
     */
    public static final class Builder<S> {
        private String name;
        private String systemPrompt = "";
        private final List<Tool> tools = new ArrayList<>();
        private final List<ToolDefinition> toolDefinitions = new ArrayList<>();
        private Function<S, S> memoryScope;

        private Builder() {}

        /**
         * Set the agent's identity. Required.
         *
         * @param name non-blank agent identifier
         * @return this builder
         */
        public Builder<S> name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Set the system prompt prepended to every LLM request.
         *
         * @param systemPrompt the role prompt; empty string disables injection
         * @return this builder
         */
        public Builder<S> systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        /**
         * Register a tool available to this agent. Multiple calls accumulate.
         *
         * @param definition tool metadata exposed to the LLM
         * @param tool       the tool implementation
         * @return this builder
         */
        public Builder<S> tool(ToolDefinition definition, Tool tool) {
            Objects.requireNonNull(definition, "definition");
            Objects.requireNonNull(tool, "tool");
            this.toolDefinitions.add(definition);
            this.tools.add(tool);
            return this;
        }

        /**
         * Optional state-to-state function for scoping memory access.
         *
         * @param memoryScope mapping from full state to the slice this agent should observe
         * @return this builder
         */
        public Builder<S> memoryScope(Function<S, S> memoryScope) {
            this.memoryScope = memoryScope;
            return this;
        }

        /**
         * Build the immutable profile.
         *
         * @return the agent profile
         */
        public AgentProfile<S> build() {
            return new AgentProfile<>(name, systemPrompt, tools, toolDefinitions, memoryScope);
        }
    }
}
