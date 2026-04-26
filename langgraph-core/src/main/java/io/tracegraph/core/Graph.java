package io.tracegraph.core;

import io.tracegraph.core.exec.Executor;
import io.tracegraph.core.exec.GraphValidationException;
import io.tracegraph.core.spi.CheckpointStore;
import io.tracegraph.core.spi.NodeListener;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

public final class Graph<S> {

    private static final int DEFAULT_MAX_STEPS = 1000;

    private final Map<String, Node<S>> nodes;
    private final Map<String, List<Edge<S>>> edgesByFrom;
    private final List<Edge<S>> edges;
    private final Set<String> terminals;
    private final String entry;
    private final NodeListener listener;
    private final int maxSteps;
    private final Map<String, RetryPolicy> nodePolicies;
    private final RetryPolicy defaultPolicy;
    private final CheckpointStore checkpointStore;

    private Graph(Builder<S> b) {
        this.nodes = Map.copyOf(b.nodes);
        Map<String, List<Edge<S>>> grouped = new LinkedHashMap<>();
        for (Edge<S> e : b.edges) {
            grouped.computeIfAbsent(e.from(), k -> new ArrayList<>()).add(e);
        }
        Map<String, List<Edge<S>>> immutable = new LinkedHashMap<>();
        grouped.forEach((k, v) -> immutable.put(k, List.copyOf(v)));
        this.edgesByFrom = Collections.unmodifiableMap(immutable);
        this.edges = List.copyOf(b.edges);
        this.terminals = Set.copyOf(b.terminals);
        this.entry = b.entry;
        this.listener = b.listener == null ? NodeListener.NOOP : b.listener;
        this.maxSteps = b.maxSteps;
        this.nodePolicies = Map.copyOf(b.nodePolicies);
        this.defaultPolicy = b.defaultPolicy == null ? RetryPolicy.none() : b.defaultPolicy;
        this.checkpointStore = b.checkpointStore == null ? CheckpointStore.noop() : b.checkpointStore;
    }

    public static <S> Builder<S> builder() {
        return new Builder<>();
    }

    public ExecutionResult<S> run(S initial) {
        return run(initial, Executor.newExecutionId());
    }

    public ExecutionResult<S> run(S initial, String executionId) {
        Objects.requireNonNull(executionId, "executionId");
        return executor().run(initial, executionId);
    }

    public Optional<ExecutionResult<S>> resume(String executionId) {
        Objects.requireNonNull(executionId, "executionId");
        ExecutionResult<S> result = executor().resume(executionId);
        return Optional.ofNullable(result);
    }

    private Executor<S> executor() {
        return new Executor<>(nodes, edgesByFrom, terminals, entry, listener, maxSteps, nodePolicies, defaultPolicy, checkpointStore);
    }

    public Set<String> nodeNames() {
        return nodes.keySet();
    }

    public List<Edge<S>> edges() {
        return edges;
    }

    public String entry() {
        return entry;
    }

    public Set<String> terminals() {
        return terminals;
    }

    public static final class Builder<S> {
        private final Map<String, Node<S>> nodes = new LinkedHashMap<>();
        private final List<Edge<S>> edges = new ArrayList<>();
        private final Set<String> terminals = new HashSet<>();
        private final Map<String, RetryPolicy> nodePolicies = new HashMap<>();
        private String entry;
        private NodeListener listener;
        private int maxSteps = DEFAULT_MAX_STEPS;
        private RetryPolicy defaultPolicy;
        private CheckpointStore checkpointStore;

        private Builder() {}

        public Builder<S> node(String name, Node<S> node) {
            return node(name, node, null);
        }

        public Builder<S> node(String name, Node<S> node, RetryPolicy retryPolicy) {
            Objects.requireNonNull(name, "node name");
            Objects.requireNonNull(node, "node");
            if (nodes.containsKey(name)) {
                throw new GraphValidationException("Duplicate node name: '" + name + "'");
            }
            nodes.put(name, node);
            if (retryPolicy != null) {
                nodePolicies.put(name, retryPolicy);
            }
            return this;
        }

        public Builder<S> edge(String from, String to) {
            return edge(from, to, null);
        }

        public Builder<S> edge(String from, String to, Predicate<S> condition) {
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
            edges.add(new Edge<>(from, to, condition));
            return this;
        }

        public Builder<S> entry(String name) {
            this.entry = Objects.requireNonNull(name, "entry");
            return this;
        }

        public Builder<S> terminal(String name) {
            terminals.add(Objects.requireNonNull(name, "terminal"));
            return this;
        }

        public Builder<S> listener(NodeListener listener) {
            this.listener = listener;
            return this;
        }

        public Builder<S> maxSteps(int maxSteps) {
            if (maxSteps <= 0) {
                throw new IllegalArgumentException("maxSteps must be > 0");
            }
            this.maxSteps = maxSteps;
            return this;
        }

        public Builder<S> defaultRetryPolicy(RetryPolicy policy) {
            this.defaultPolicy = Objects.requireNonNull(policy, "policy");
            return this;
        }

        public Builder<S> checkpointStore(CheckpointStore store) {
            this.checkpointStore = Objects.requireNonNull(store, "store");
            return this;
        }

        public Graph<S> build() {
            validate();
            return new Graph<>(this);
        }

        private void validate() {
            if (entry == null) {
                throw new GraphValidationException("Graph has no entry node — call .entry(name)");
            }
            if (!nodes.containsKey(entry)) {
                throw new GraphValidationException("Entry node '" + entry + "' is not declared");
            }
            for (Edge<S> e : edges) {
                if (!nodes.containsKey(e.from())) {
                    throw new GraphValidationException("Edge.from references unknown node: '" + e.from() + "'");
                }
                if (!nodes.containsKey(e.to())) {
                    throw new GraphValidationException("Edge.to references unknown node: '" + e.to() + "'");
                }
            }
            for (String t : terminals) {
                if (!nodes.containsKey(t)) {
                    throw new GraphValidationException("Terminal node '" + t + "' is not declared");
                }
            }
            assertReachable();
            assertNoDeadEnds();
        }

        private void assertReachable() {
            Set<String> seen = new HashSet<>();
            Deque<String> stack = new ArrayDeque<>();
            stack.push(entry);
            while (!stack.isEmpty()) {
                String n = stack.pop();
                if (!seen.add(n)) continue;
                for (Edge<S> e : edges) {
                    if (e.from().equals(n)) {
                        stack.push(e.to());
                    }
                }
            }
            for (String n : nodes.keySet()) {
                if (!seen.contains(n)) {
                    throw new GraphValidationException("Node '" + n + "' is unreachable from entry '" + entry + "'");
                }
            }
        }

        private void assertNoDeadEnds() {
            for (String name : nodes.keySet()) {
                if (terminals.contains(name)) continue;
                boolean hasOutgoing = edges.stream().anyMatch(e -> e.from().equals(name));
                if (!hasOutgoing) {
                    throw new GraphValidationException(
                            "Node '" + name + "' has no outgoing edges and is not declared terminal");
                }
            }
        }
    }
}
