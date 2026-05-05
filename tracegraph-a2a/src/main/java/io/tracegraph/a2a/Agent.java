package io.tracegraph.a2a;

import io.tracegraph.core.ExecutionResult;
import io.tracegraph.core.Graph;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * A named participant on an {@link AgentBus} backed by a {@link Graph}.
 *
 * <p>Calling {@link #start} registers a subscription: each inbound message is converted
 * to the graph's state type via {@code payloadExtractor}, the graph runs, and the optional
 * {@code replyHandler} is invoked with the original message and the execution result.
 *
 * @param <S> the graph state type
 */
public final class Agent<S> {

    private final String id;
    private final Graph<S> graph;
    private final AgentBus bus;

    private Agent(String id, Graph<S> graph, AgentBus bus) {
        this.id = id;
        this.graph = graph;
        this.bus = bus;
    }

    public String id() {
        return id;
    }

    /**
     * Start listening on the bus.
     *
     * @param payloadExtractor converts an inbound {@link AgentMessage} to the graph's seed state
     * @param replyHandler     optional; called after each graph run with the original message and result
     * @return a {@link Runnable} that, when invoked, stops this agent from receiving messages
     */
    public Runnable start(Function<AgentMessage, S> payloadExtractor,
                          BiConsumer<AgentMessage, ExecutionResult<S>> replyHandler) {
        Objects.requireNonNull(payloadExtractor, "payloadExtractor");
        return bus.subscribe(id, message -> {
            S input = payloadExtractor.apply(message);
            ExecutionResult<S> result = graph.run(input);
            if (replyHandler != null) {
                replyHandler.accept(message, result);
            }
        });
    }

    public static <S> Builder<S> builder() {
        return new Builder<>();
    }

    public static final class Builder<S> {
        private String id;
        private Graph<S> graph;
        private AgentBus bus;

        public Builder<S> id(String id) {
            this.id = id;
            return this;
        }

        public Builder<S> graph(Graph<S> graph) {
            this.graph = graph;
            return this;
        }

        public Builder<S> bus(AgentBus bus) {
            this.bus = bus;
            return this;
        }

        public Agent<S> build() {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(graph, "graph");
            Objects.requireNonNull(bus, "bus");
            return new Agent<>(id, graph, bus);
        }
    }
}
