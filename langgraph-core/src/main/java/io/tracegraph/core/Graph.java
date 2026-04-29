package io.tracegraph.core;

import io.tracegraph.core.exec.Executor;
import io.tracegraph.core.exec.GraphValidationException;
import io.tracegraph.core.exec.NodeKind;
import io.tracegraph.core.spi.CheckpointStore;
import io.tracegraph.core.spi.MemoryStore;
import io.tracegraph.core.spi.NodeListener;
import io.tracegraph.core.spi.TraceRecorder;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.function.Predicate;

public final class Graph<S> {

    private static final int DEFAULT_MAX_STEPS = 1000;

    private final Map<String, NodeKind<S>> nodes;
    private final Map<String, List<Edge<S>>> edgesByFrom;
    private final List<Edge<S>> edges;
    private final Set<String> terminals;
    private final String entry;
    private final NodeListener listener;
    private final int maxSteps;
    private final Map<String, RetryPolicy> nodePolicies;
    private final RetryPolicy defaultPolicy;
    private final CheckpointStore checkpointStore;
    private final TraceRecorder traceRecorder;
    private final MemoryStore memoryStore;
    private final ExecutorService userExecutor;
    private final Set<String> interruptBefore;
    private final Set<String> interruptAfter;

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
        this.traceRecorder = b.traceRecorder == null ? TraceRecorder.noop() : b.traceRecorder;
        this.memoryStore = b.memoryStore == null ? MemoryStore.noop() : b.memoryStore;
        this.userExecutor = b.userExecutor;
        this.interruptBefore = Set.copyOf(b.interruptBefore);
        this.interruptAfter = Set.copyOf(b.interruptAfter);
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

    public ExecutionResult<S> runFrom(String startNode, S seed, String executionId) {
        Objects.requireNonNull(startNode, "startNode");
        Objects.requireNonNull(executionId, "executionId");
        return executor().runFrom(startNode, seed, executionId);
    }

    public Flow.Publisher<NodeEvent<S>> stream(S initial) {
        return stream(initial, Executor.newExecutionId());
    }

    public Flow.Publisher<NodeEvent<S>> stream(S initial, String executionId) {
        Objects.requireNonNull(executionId, "executionId");
        java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(1);
        SubmissionPublisher<NodeEvent<S>> pub = new SubmissionPublisher<>() {
            @Override
            public void subscribe(Flow.Subscriber<? super NodeEvent<S>> subscriber) {
                super.subscribe(subscriber);
                ready.countDown();
            }
        };
        NodeListener streamingListener = new StreamingNodeListener<>(executionId, pub);
        NodeListener composed = composeListeners(this.listener, streamingListener);
        Graph<S> withStream = withListener(composed);
        Thread.startVirtualThread(() -> {
            try {
                ready.await();
                ExecutionResult<S> r = withStream.run(initial, executionId);
                List<String> path = r.path();
                String lastNode = path.isEmpty() ? "" : path.get(path.size() - 1);
                pub.submit(new NodeEvent.Complete<>(executionId, lastNode, r));
                pub.close();
            } catch (Throwable t) {
                pub.closeExceptionally(t);
            }
        });
        return pub;
    }

    private static <S> NodeListener composeListeners(NodeListener a, NodeListener b) {
        if (a == NodeListener.NOOP) return b;
        return new NodeListener() {
            @Override public void onEnter(String n, Object s) { a.onEnter(n, s); b.onEnter(n, s); }
            @Override public void onState(String n, Object before, Object after) { a.onState(n, before, after); b.onState(n, before, after); }
            @Override public void onRetry(String n, int att, Throwable c) { a.onRetry(n, att, c); b.onRetry(n, att, c); }
            @Override public void onError(String n, Throwable c) { a.onError(n, c); b.onError(n, c); }
        };
    }

    private Graph<S> withListener(NodeListener l) {
        Builder<S> b = new Builder<>();
        b.nodes.putAll(this.nodes);
        b.edges.addAll(this.edges);
        b.terminals.addAll(this.terminals);
        b.entry = this.entry;
        b.listener = l;
        b.maxSteps = this.maxSteps;
        b.nodePolicies.putAll(this.nodePolicies);
        b.defaultPolicy = this.defaultPolicy;
        b.checkpointStore = this.checkpointStore;
        b.traceRecorder = this.traceRecorder;
        b.memoryStore = this.memoryStore;
        b.userExecutor = this.userExecutor;
        b.interruptBefore.addAll(this.interruptBefore);
        b.interruptAfter.addAll(this.interruptAfter);
        return new Graph<>(b);
    }

    public TraceRecorder traceRecorder() {
        return traceRecorder;
    }

    public MemoryStore memoryStore() {
        return memoryStore;
    }

    private Executor<S> executor() {
        return new Executor<>(nodes, edgesByFrom, terminals, entry, listener, maxSteps,
                nodePolicies, defaultPolicy, checkpointStore, traceRecorder, memoryStore, userExecutor,
                interruptBefore, interruptAfter);
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

    public Optional<Graph<S>> subgraph(String nodeName) {
        NodeKind<S> kind = nodes.get(nodeName);
        if (kind instanceof NodeKind.Subgraph<S> sg) return Optional.of(sg.inner());
        return Optional.empty();
    }

    public String toMermaid() {
        return io.tracegraph.core.viz.MermaidRenderer.render(this);
    }

    public String toPlantUml() {
        return io.tracegraph.core.viz.PlantUmlRenderer.render(this);
    }

    public static final class Builder<S> {
        private final Map<String, NodeKind<S>> nodes = new LinkedHashMap<>();
        private final List<Edge<S>> edges = new ArrayList<>();
        private final Set<String> terminals = new HashSet<>();
        private final Map<String, RetryPolicy> nodePolicies = new HashMap<>();
        private final Set<String> interruptBefore = new HashSet<>();
        private final Set<String> interruptAfter = new HashSet<>();
        private String entry;
        private NodeListener listener;
        private int maxSteps = DEFAULT_MAX_STEPS;
        private RetryPolicy defaultPolicy;
        private CheckpointStore checkpointStore;
        private TraceRecorder traceRecorder;
        private MemoryStore memoryStore;
        private ExecutorService userExecutor;

        private Builder() {}

        public Builder<S> node(String name, Node<S> node) {
            return node(name, node, null);
        }

        public Builder<S> node(String name, Node<S> node, RetryPolicy retryPolicy) {
            register(name, NodeKind.sync(node), retryPolicy);
            return this;
        }

        public Builder<S> asyncNode(String name, AsyncNode<S> node) {
            return asyncNode(name, node, null);
        }

        public Builder<S> asyncNode(String name, AsyncNode<S> node, RetryPolicy retryPolicy) {
            register(name, NodeKind.async(node), retryPolicy);
            return this;
        }

        public Builder<S> parallel(String name, List<Node<S>> branches, Merger<S> merger) {
            return parallel(name, branches, merger, null);
        }

        public Builder<S> parallel(String name, List<Node<S>> branches, Merger<S> merger, RetryPolicy retryPolicy) {
            Objects.requireNonNull(branches, "branches");
            if (branches.isEmpty()) {
                throw new GraphValidationException("Parallel '" + name + "' must have at least one branch");
            }
            List<NodeKind<S>> kinds = new ArrayList<>(branches.size());
            for (Node<S> b : branches) kinds.add(NodeKind.sync(b));
            register(name, NodeKind.parallel(kinds, Objects.requireNonNull(merger, "merger")), retryPolicy);
            return this;
        }

        public Builder<S> parallelAsync(String name, List<AsyncNode<S>> branches, Merger<S> merger) {
            return parallelAsync(name, branches, merger, null);
        }

        public Builder<S> parallelAsync(String name, List<AsyncNode<S>> branches, Merger<S> merger, RetryPolicy retryPolicy) {
            Objects.requireNonNull(branches, "branches");
            if (branches.isEmpty()) {
                throw new GraphValidationException("Parallel '" + name + "' must have at least one branch");
            }
            List<NodeKind<S>> kinds = new ArrayList<>(branches.size());
            for (AsyncNode<S> b : branches) kinds.add(NodeKind.async(b));
            register(name, NodeKind.parallel(kinds, Objects.requireNonNull(merger, "merger")), retryPolicy);
            return this;
        }

        private void register(String name, NodeKind<S> kind, RetryPolicy retryPolicy) {
            Objects.requireNonNull(name, "node name");
            Objects.requireNonNull(kind, "node");
            if (nodes.containsKey(name)) {
                throw new GraphValidationException("Duplicate node name: '" + name + "'");
            }
            nodes.put(name, kind);
            if (retryPolicy != null) {
                nodePolicies.put(name, retryPolicy);
            }
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

        public Builder<S> traceRecorder(TraceRecorder recorder) {
            this.traceRecorder = Objects.requireNonNull(recorder, "recorder");
            return this;
        }

        public Builder<S> memoryStore(MemoryStore store) {
            this.memoryStore = Objects.requireNonNull(store, "store");
            return this;
        }

        public Builder<S> executor(ExecutorService executor) {
            this.userExecutor = Objects.requireNonNull(executor, "executor");
            return this;
        }

        public Builder<S> routingNode(String name, RoutingNode<S> node) {
            return routingNode(name, node, null);
        }

        public Builder<S> routingNode(String name, RoutingNode<S> node, RetryPolicy retryPolicy) {
            Objects.requireNonNull(node, "node");
            register(name, NodeKind.routing(node), retryPolicy);
            return this;
        }

        /**
         * Embeds a compiled graph as a node. The inner graph runs to completion as a single step
         * in the outer graph. Both graphs must share the same state type {@code <S>}.
         *
         * <p>Mid-subgraph crash semantics: the entire subgraph re-runs from its start on resume.
         * Resuming a parent execution into the middle of a subgraph is not supported.
         */
        public Builder<S> subgraph(String name, Graph<S> inner) {
            return subgraph(name, inner, null);
        }

        public Builder<S> subgraph(String name, Graph<S> inner, RetryPolicy retryPolicy) {
            Objects.requireNonNull(inner, "inner");
            register(name, NodeKind.subgraph(inner), retryPolicy);
            return this;
        }

        public Builder<S> interruptBefore(String... names) {
            for (String n : names) interruptBefore.add(Objects.requireNonNull(n, "interruptBefore name"));
            return this;
        }

        public Builder<S> interruptAfter(String... names) {
            for (String n : names) interruptAfter.add(Objects.requireNonNull(n, "interruptAfter name"));
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
            for (String n : interruptBefore) {
                if (!nodes.containsKey(n)) {
                    throw new GraphValidationException("interruptBefore references unknown node: '" + n + "'");
                }
            }
            for (String n : interruptAfter) {
                if (!nodes.containsKey(n)) {
                    throw new GraphValidationException("interruptAfter references unknown node: '" + n + "'");
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
