package io.tracegraph.core;

import io.tracegraph.core.exec.Executor;
import io.tracegraph.core.exec.GraphValidationException;
import io.tracegraph.core.exec.NodeKind;
import io.tracegraph.core.spi.CheckpointStore;
import io.tracegraph.core.spi.MemoryStore;
import io.tracegraph.core.spi.NodeListener;
import io.tracegraph.core.spi.TraceRecorder;
import io.tracegraph.core.spi.VectorStore;

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
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A typed, immutable graph of {@link Node}s connected by {@link Edge}s. Constructed via the fluent
 * {@link Graph.Builder}, validated eagerly on {@link Builder#build()}, and safe to share across
 * threads.
 *
 * <p>Three executor entry points: {@link #run(Object)} starts a fresh execution from the entry node,
 * {@link #resume(String)} continues from the last checkpointed node, and
 * {@link #runFrom(String, Object, String)} starts at an arbitrary node (used by replay/fork).
 *
 * <p>SPIs plug in via the builder: {@link NodeListener} for span-shaped observability,
 * {@link TraceRecorder} for executionId-aware trace capture, {@link CheckpointStore} for durable
 * resume, {@link MemoryStore} for cross-execution data.
 *
 * @param <S> the state type carried through the graph
 */
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
    private final VectorStore vectorStore;
    private final ExecutorService userExecutor;
    private final Set<String> interruptBefore;
    private final Set<String> interruptAfter;
    private final Supplier<String> executionIdFactory;
    private final Class<S> stateType;

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
        this.vectorStore = b.vectorStore == null ? VectorStore.noop() : b.vectorStore;
        this.userExecutor = b.userExecutor;
        this.interruptBefore = Set.copyOf(b.interruptBefore);
        this.interruptAfter = Set.copyOf(b.interruptAfter);
        this.executionIdFactory = b.executionIdFactory;
        this.stateType = b.stateType;
    }

    /**
     * Create a new graph builder.
     *
     * @param <S> the state type carried through the graph
     * @return a fresh builder
     */
    public static <S> Builder<S> builder() {
        return new Builder<>();
    }

    /**
     * Execute the graph from its configured entry node with a generated execution id.
     *
     * @param initial initial state
     * @return execution result
     */
    public ExecutionResult<S> run(S initial) {
        Objects.requireNonNull(initial, "initial state");
        return run(initial, executionIdFactory.get());
    }

    /**
     * Execute the graph from its configured entry node using a caller-supplied execution id.
     *
     * @param initial initial state
     * @param executionId stable execution id for checkpointing, tracing, and correlation
     * @return execution result
     */
    public ExecutionResult<S> run(S initial, String executionId) {
        Objects.requireNonNull(initial, "initial state");
        Objects.requireNonNull(executionId, "executionId");
        return executor().run(initial, executionId);
    }

    /**
     * Resume a previously checkpointed execution.
     *
     * @param executionId execution id to resume
     * @return resumed execution result when a checkpoint exists, otherwise an empty optional
     */
    public Optional<ExecutionResult<S>> resume(String executionId) {
        Objects.requireNonNull(executionId, "executionId");
        ExecutionResult<S> result = executor().resume(executionId);
        return Optional.ofNullable(result);
    }

    /**
     * Resume a previously checkpointed execution, replacing the saved state with a caller-supplied
     * override before continuing. This is the HITL state-edit entry point: a human reviews the
     * interrupted state, edits it, and resumes with the corrected version.
     *
     * @param executionId   execution id to resume
     * @param stateOverride state to inject in place of the checkpoint's saved state
     * @return resumed execution result when a checkpoint exists, otherwise an empty optional
     */
    public Optional<ExecutionResult<S>> resume(String executionId, S stateOverride) {
        Objects.requireNonNull(executionId, "executionId");
        Objects.requireNonNull(stateOverride, "stateOverride");
        ExecutionResult<S> result = executor().resume(executionId, stateOverride);
        return Optional.ofNullable(result);
    }

    /**
     * Returns the runtime state class configured via {@link Builder#stateType(Class)}, or empty if
     * not set. Used by REST adapters that need to deserialize a JSON body into the graph's state
     * type (e.g. the HITL resume-with-state-edit endpoint).
     *
     * @return the configured state class, or {@link Optional#empty()}
     */
    public Optional<Class<S>> stateType() {
        return Optional.ofNullable(stateType);
    }

    /**
     * Start execution at an arbitrary node.
     *
     * <p>This is primarily useful for replay, forked execution, and advanced tooling.
     *
     * @param startNode node name to begin at
     * @param seed state to inject at that node
     * @param executionId execution id to use for the forked run
     * @return execution result
     */
    public ExecutionResult<S> runFrom(String startNode, S seed, String executionId) {
        Objects.requireNonNull(startNode, "startNode");
        Objects.requireNonNull(seed, "initial state");
        Objects.requireNonNull(executionId, "executionId");
        return executor().runFrom(startNode, seed, executionId);
    }

    /**
     * Stream node lifecycle events while executing from the entry node.
     *
     * @param initial initial state
     * @return publisher of execution events
     */
    public Flow.Publisher<NodeEvent<S>> stream(S initial) {
        Objects.requireNonNull(initial, "initial state");
        return stream(initial, executionIdFactory.get());
    }

    /**
     * Stream node lifecycle events while executing with a caller-supplied execution id.
     *
     * @param initial initial state
     * @param executionId execution id for the streamed run
     * @return publisher of execution events
     */
    public Flow.Publisher<NodeEvent<S>> stream(S initial, String executionId) {
        Objects.requireNonNull(initial, "initial state");
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
                if (r.error() != null) {
                    List<String> path = r.path();
                    String lastNode = path.isEmpty() ? "" : path.get(path.size() - 1);
                    NodeEvent<S> failedEvent = new NodeEvent.Failed<>(executionId, lastNode, r.error());
                    // Use offer with retry to ensure the event is buffered before we close
                    for (int i = 0; i < 200; i++) {
                        if (pub.offer(failedEvent, 50, java.util.concurrent.TimeUnit.MILLISECONDS,
                                (sub, evt) -> false) >= 0) {
                            break;
                        }
                        Thread.sleep(1);
                    }
                    // Small delay to allow subscriber to drain the event
                    Thread.sleep(50);
                    pub.closeExceptionally(r.error());
                } else {
                    List<String> path = r.path();
                    String lastNode = path.isEmpty() ? "" : path.get(path.size() - 1);
                    pub.submit(new NodeEvent.Complete<>(executionId, lastNode, r));
                    pub.close();
                }
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
            @Override public void onExit(String n, Object s) { a.onExit(n, s); b.onExit(n, s); }
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
        b.vectorStore = this.vectorStore;
        b.userExecutor = this.userExecutor;
        b.interruptBefore.addAll(this.interruptBefore);
        b.interruptAfter.addAll(this.interruptAfter);
        b.executionIdFactory = this.executionIdFactory;
        return new Graph<>(b);
    }

    public TraceRecorder traceRecorder() {
        return traceRecorder;
    }

    /**
     * Return the memory store configured on the graph.
     *
     * @return configured memory store, or the no-op implementation when none was supplied
     */
    public MemoryStore memoryStore() {
        return memoryStore;
    }

    /**
     * Return the vector store configured on the graph.
     *
     * @return configured vector store, or the no-op implementation when none was supplied
     */
    public VectorStore vectorStore() {
        return vectorStore;
    }

    private Executor<S> executor() {
        return new Executor<>(nodes, edgesByFrom, terminals, entry, listener, maxSteps,
                nodePolicies, defaultPolicy, checkpointStore, traceRecorder, memoryStore, vectorStore,
                userExecutor, interruptBefore, interruptAfter);
    }

    public Set<String> nodeNames() {
        return nodes.keySet();
    }

    /**
     * Return all declared edges in builder insertion order.
     *
     * @return immutable edge list
     */
    public List<Edge<S>> edges() {
        return edges;
    }

    /**
     * Return the configured entry node name.
     *
     * @return entry node name
     */
    public String entry() {
        return entry;
    }

    /**
     * Return the names of nodes declared terminal.
     *
     * @return immutable terminal node set
     */
    public Set<String> terminals() {
        return terminals;
    }

    /**
     * Resolve a named embedded subgraph when the node was registered through
     * {@link Builder#subgraph(String, Graph)}.
     *
     * @param nodeName node to inspect
     * @return embedded subgraph if present
     */
    public Optional<Graph<S>> subgraph(String nodeName) {
        NodeKind<S> kind = nodes.get(nodeName);
        if (kind instanceof NodeKind.Subgraph<S> sg) return Optional.of(sg.inner());
        return Optional.empty();
    }

    /**
     * Return the branch count for each parallel node, keyed by node name.
     * Non-parallel nodes are not included.
     *
     * @return immutable map of parallel node name to branch count
     */
    public Map<String, Integer> parallelBranchCounts() {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<String, NodeKind<S>> e : nodes.entrySet()) {
            if (e.getValue() instanceof NodeKind.Parallel<S> p) {
                result.put(e.getKey(), p.branches().size());
            }
        }
        return Map.copyOf(result);
    }

    /**
     * Compute structural complexity metrics for this graph.
     *
     * @return complexity record
     */
    public io.tracegraph.core.analysis.GraphComplexity complexity() {
        return io.tracegraph.core.analysis.GraphComplexity.of(this);
    }

    /**
     * Render this graph as Mermaid flowchart source.
     *
     * @return Mermaid source
     */
    public String toMermaid() {
        return io.tracegraph.core.viz.MermaidRenderer.render(this);
    }

    /**
     * Render this graph as PlantUML activity-style source.
     *
     * @return PlantUML source
     */
    public String toPlantUml() {
        return io.tracegraph.core.viz.PlantUmlRenderer.render(this);
    }

    /**
     * Fluent builder for immutable {@link Graph} instances.
     *
     * <p>The builder is mutable and not thread-safe. Build once configuration is complete, then
     * share the resulting graph across threads or executions.
     *
     * @param <S> the graph state type
     */
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
        private VectorStore vectorStore;
        private ExecutorService userExecutor;
        private Supplier<String> executionIdFactory = () -> UUID.randomUUID().toString();
        private Class<S> stateType;

        private Builder() {}

        /**
         * Declares the runtime class of the state type, enabling REST-layer JSON deserialization
         * for endpoints that accept state as a request body (e.g. fork with seed override).
         *
         * @param stateType the concrete state class
         * @return this builder
         */
        public Builder<S> stateType(Class<S> stateType) {
            this.stateType = Objects.requireNonNull(stateType, "stateType");
            return this;
        }

        /**
         * Register a synchronous node.
         *
         * @param name unique node name
         * @param node node implementation
         * @return this builder
         */
        public Builder<S> node(String name, Node<S> node) {
            return node(name, node, null);
        }

        /**
         * Register a synchronous node with a per-node retry policy.
         *
         * @param name unique node name
         * @param node node implementation
         * @param retryPolicy retry policy to apply to failures from this node
         * @return this builder
         */
        public Builder<S> node(String name, Node<S> node, RetryPolicy retryPolicy) {
            register(name, NodeKind.sync(node), retryPolicy);
            return this;
        }

        public Builder<S> routingNode(String name, RoutingNode<S> node) {
            return routingNode(name, node, null);
        }

        /**
         * Register a routing node with a per-node retry policy.
         *
         * @param name unique node name
         * @param node routing node implementation
         * @param retryPolicy retry policy to apply to failures from this node
         * @return this builder
         */
        public Builder<S> routingNode(String name, RoutingNode<S> node, RetryPolicy retryPolicy) {
            register(name, NodeKind.routing(node), retryPolicy);
            return this;
        }

        public Builder<S> asyncNode(String name, AsyncNode<S> node) {
            return asyncNode(name, node, null);
        }

        /**
         * Register an asynchronous node with a per-node retry policy.
         *
         * @param name unique node name
         * @param node async node implementation
         * @param retryPolicy retry policy to apply to failures from this node
         * @return this builder
         */
        public Builder<S> asyncNode(String name, AsyncNode<S> node, RetryPolicy retryPolicy) {
            register(name, NodeKind.async(node), retryPolicy);
            return this;
        }

        public Builder<S> parallel(String name, List<Node<S>> branches, Merger<S> merger) {
            return parallel(name, branches, merger, null);
        }

        /**
         * Register a synchronous parallel node.
         *
         * @param name unique node name
         * @param branches branches to execute concurrently
         * @param merger combines branch outputs into the next state
         * @param retryPolicy retry policy to apply to the whole parallel step
         * @return this builder
         */
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

        /**
         * Register an asynchronous parallel node.
         *
         * @param name unique node name
         * @param branches async branches to execute concurrently
         * @param merger combines branch outputs into the next state
         * @param retryPolicy retry policy to apply to the whole parallel step
         * @return this builder
         */
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

        /**
         * Register an outgoing edge between two nodes.
         *
         * <p>Edges are evaluated in declaration order. For conditional routing, prefer a routing
         * node when the destination cannot be described by a small set of predicates.
         *
         * @param from source node
         * @param to destination node
         * @param condition optional condition that must match for the edge to be taken
         * @return this builder
         */
        public Builder<S> edge(String from, String to, Predicate<S> condition) {
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
            edges.add(new Edge<>(from, to, condition));
            return this;
        }

        /**
         * Set the graph entry node.
         *
         * @param name entry node name
         * @return this builder
         */
        public Builder<S> entry(String name) {
            this.entry = Objects.requireNonNull(name, "entry");
            return this;
        }

        /**
         * Mark a node as terminal.
         *
         * @param name terminal node name
         * @return this builder
         */
        public Builder<S> terminal(String name) {
            terminals.add(Objects.requireNonNull(name, "terminal"));
            return this;
        }

        /**
         * Attach a node listener for lifecycle callbacks.
         *
         * @param listener listener to attach
         * @return this builder
         */
        public Builder<S> listener(NodeListener listener) {
            this.listener = listener;
            return this;
        }

        /**
         * Set the maximum number of node steps allowed in one execution.
         *
         * @param maxSteps positive step limit
         * @return this builder
         */
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

        /**
         * Configure checkpoint persistence for resume support.
         *
         * @param store checkpoint store
         * @return this builder
         */
        public Builder<S> checkpointStore(CheckpointStore store) {
            this.checkpointStore = Objects.requireNonNull(store, "store");
            return this;
        }

        /**
         * Configure execution trace recording.
         *
         * @param recorder trace recorder
         * @return this builder
         */
        public Builder<S> traceRecorder(TraceRecorder recorder) {
            this.traceRecorder = Objects.requireNonNull(recorder, "recorder");
            return this;
        }

        /**
         * Configure shared execution memory.
         *
         * @param store memory store
         * @return this builder
         */
        public Builder<S> memoryStore(MemoryStore store) {
            this.memoryStore = Objects.requireNonNull(store, "store");
            return this;
        }

        /**
         * Configure a vector store for nearest-neighbour recall. Exposed to nodes via
         * {@link Context#vectorStore()}.
         *
         * @param store vector store
         * @return this builder
         */
        public Builder<S> vectorStore(VectorStore store) {
            this.vectorStore = Objects.requireNonNull(store, "store");
            return this;
        }

        /**
         * Provide an executor service for async and parallel work.
         *
         * <p>Caller owns the executor lifecycle — TraceGraph never calls {@code shutdown()} on it.
         * When omitted, TraceGraph creates a virtual-thread-per-task executor per run and
         * automatically closes it on completion.
         *
         * @param executor executor service to use
         * @return this builder
         */
        public Builder<S> executor(ExecutorService executor) {
            this.userExecutor = Objects.requireNonNull(executor, "executor");
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

        public Builder<S> executionIdFactory(Supplier<String> factory) {
            this.executionIdFactory = Objects.requireNonNull(factory, "factory");
            return this;
        }

        public Builder<S> interruptBefore(String... names) {
            for (String n : names) interruptBefore.add(Objects.requireNonNull(n, "interruptBefore name"));
            return this;
        }

        /**
         * Request an interrupt checkpoint after the named nodes complete.
         *
         * @param names node names
         * @return this builder
         */
        public Builder<S> interruptAfter(String... names) {
            for (String n : names) interruptAfter.add(Objects.requireNonNull(n, "interruptAfter name"));
            return this;
        }

        /**
         * Validate the configured graph and create an immutable runtime instance.
         *
         * @return compiled graph
         */
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
            boolean hasRouting = nodes.values().stream().anyMatch(k -> k instanceof NodeKind.Routing<?>);
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
                if (hasRouting && nodes.get(n) instanceof NodeKind.Routing<?>) {
                    nodes.keySet().forEach(stack::push);
                }
            }
            for (String n : nodes.keySet()) {
                if (!seen.contains(n)) {
                    throw new GraphValidationException("Node '" + n + "' is unreachable from entry '" + entry + "'");
                }
            }
        }

        private void assertNoDeadEnds() {
            for (Map.Entry<String, NodeKind<S>> entry : nodes.entrySet()) {
                String name = entry.getKey();
                if (terminals.contains(name)) continue;
                if (entry.getValue() instanceof NodeKind.Routing<?>) continue;
                boolean hasOutgoing = edges.stream().anyMatch(e -> e.from().equals(name));
                if (!hasOutgoing) {
                    throw new GraphValidationException(
                            "Node '" + name + "' has no outgoing edges and is not declared terminal");
                }
            }
        }
    }
}
