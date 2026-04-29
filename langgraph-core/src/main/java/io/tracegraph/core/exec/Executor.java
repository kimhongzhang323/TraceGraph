package io.tracegraph.core.exec;

import io.tracegraph.core.Checkpoint;
import io.tracegraph.core.Context;
import io.tracegraph.core.Edge;
import io.tracegraph.core.ExecutionResult;
import io.tracegraph.core.RetryPolicy;
import io.tracegraph.core.Status;
import io.tracegraph.core.spi.CheckpointStore;
import io.tracegraph.core.spi.MemoryStore;
import io.tracegraph.core.spi.NodeListener;
import io.tracegraph.core.spi.TraceRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Executor<S> {

    private static final Logger LOG = LoggerFactory.getLogger(Executor.class);

    private final Map<String, NodeKind<S>> nodes;
    private final Map<String, List<Edge<S>>> edgesByFrom;
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
    private final Sleeper sleeper;

    public Executor(Map<String, NodeKind<S>> nodes,
                    Map<String, List<Edge<S>>> edgesByFrom,
                    Set<String> terminals,
                    String entry,
                    NodeListener listener,
                    int maxSteps,
                    Map<String, RetryPolicy> nodePolicies,
                    RetryPolicy defaultPolicy,
                    CheckpointStore checkpointStore,
                    TraceRecorder traceRecorder,
                    MemoryStore memoryStore,
                    ExecutorService userExecutor) {
        this(nodes, edgesByFrom, terminals, entry, listener, maxSteps, nodePolicies, defaultPolicy,
                checkpointStore, traceRecorder, memoryStore, userExecutor, Sleeper.realtime());
    }

    Executor(Map<String, NodeKind<S>> nodes,
             Map<String, List<Edge<S>>> edgesByFrom,
             Set<String> terminals,
             String entry,
             NodeListener listener,
             int maxSteps,
             Map<String, RetryPolicy> nodePolicies,
             RetryPolicy defaultPolicy,
             CheckpointStore checkpointStore,
             TraceRecorder traceRecorder,
             MemoryStore memoryStore,
             ExecutorService userExecutor,
             Sleeper sleeper) {
        this.nodes = nodes;
        this.edgesByFrom = edgesByFrom;
        this.terminals = terminals;
        this.entry = entry;
        this.listener = listener;
        this.maxSteps = maxSteps;
        this.nodePolicies = nodePolicies;
        this.defaultPolicy = defaultPolicy;
        this.checkpointStore = checkpointStore;
        this.traceRecorder = traceRecorder;
        this.memoryStore = memoryStore;
        this.userExecutor = userExecutor;
        this.sleeper = sleeper;
    }

    public ExecutionResult<S> run(S initial, String executionId) {
        traceRecorder.recordStart(executionId, initial);
        ExecutionResult<S> result = withExecutor(exec -> loop(executionId, initial, entry, new ArrayList<>(), exec));
        traceRecorder.recordComplete(executionId, result.status(), result.finalState());
        return result;
    }

    public ExecutionResult<S> runFrom(String startNode, S seed, String executionId) {
        if (!nodes.containsKey(startNode)) {
            throw new GraphValidationException("Start node '" + startNode + "' is not declared");
        }
        traceRecorder.recordStart(executionId, seed);
        ExecutionResult<S> result = withExecutor(exec -> loop(executionId, seed, startNode, new ArrayList<>(), exec));
        traceRecorder.recordComplete(executionId, result.status(), result.finalState());
        return result;
    }

    @SuppressWarnings("unchecked")
    public ExecutionResult<S> resume(String executionId) {
        var maybe = checkpointStore.latest(executionId);
        if (maybe.isEmpty()) return null;

        Checkpoint<S> cp = (Checkpoint<S>) maybe.get();
        S state = cp.state();
        String last = cp.lastCompletedNode();

        traceRecorder.recordStart(executionId, state);

        ExecutionResult<S> result;
        if (terminals.contains(last)) {
            result = new ExecutionResult<>(executionId, state, List.of(), Status.COMPLETED, null);
        } else {
            String next = pickNext(last, state);
            if (next == null) {
                result = new ExecutionResult<>(executionId, state, List.of(), Status.COMPLETED, null);
            } else {
                result = withExecutor(exec -> loop(executionId, state, next, new ArrayList<>(), exec));
            }
        }
        traceRecorder.recordComplete(executionId, result.status(), result.finalState());
        return result;
    }

    private ExecutionResult<S> withExecutor(java.util.function.Function<ExecutorService, ExecutionResult<S>> work) {
        if (userExecutor != null) {
            return work.apply(userExecutor);
        }
        try (ExecutorService owned = Executors.newVirtualThreadPerTaskExecutor()) {
            return work.apply(owned);
        }
    }

    private ExecutionResult<S> loop(String executionId, S initial, String startNode, List<String> path, ExecutorService exec) {
        S state = initial;
        String current = startNode;
        int steps = 0;

        while (current != null) {
            if (steps++ >= maxSteps) {
                LOG.warn("[{}] max-step guard ({}) reached at node '{}'", executionId, maxSteps, current);
                return new ExecutionResult<>(executionId, state, path, Status.HALTED, null);
            }

            NodeKind<S> node = nodes.get(current);
            RetryPolicy policy = nodePolicies.getOrDefault(current, defaultPolicy);
            path.add(current);

            listener.onEnter(current, state);
            traceRecorder.recordEnter(executionId, current, 1, state);
            S before = state;
            long startNanos = System.nanoTime();
            NodeOutcome<S> outcome = invokeWithRetry(node, state, current, executionId, policy, exec);
            long durationNanos = System.nanoTime() - startNanos;
            if (outcome.failure != null) {
                Throwable err = outcome.failure.getCause() != null ? outcome.failure.getCause() : outcome.failure;
                listener.onError(current, err);
                traceRecorder.recordError(executionId, current, err);
                return new ExecutionResult<>(executionId, state, path, Status.FAILED, outcome.failure);
            }
            state = outcome.state;
            listener.onState(current, before, state);
            traceRecorder.recordExit(executionId, current, outcome.attempts, before, state, durationNanos);
            checkpointStore.save(new Checkpoint<>(executionId, current, state, Instant.now(), false));
            listener.onExit(current, state);

            if (terminals.contains(current)) {
                return new ExecutionResult<>(executionId, state, path, Status.COMPLETED, null);
            }

            String next = pickNext(current, state);
            if (next == null) {
                return new ExecutionResult<>(executionId, state, path, Status.COMPLETED, null);
            }
            current = next;
        }

        return new ExecutionResult<>(executionId, state, path, Status.COMPLETED, null);
    }

    private String pickNext(String from, S state) {
        for (Edge<S> edge : edgesByFrom.getOrDefault(from, List.of())) {
            if (edge.matches(state)) {
                return edge.to();
            }
        }
        return null;
    }

    private NodeOutcome<S> invokeWithRetry(NodeKind<S> node, S state, String name, String executionId,
                                           RetryPolicy policy, ExecutorService exec) {
        Throwable last = null;
        for (int attempt = 1; attempt <= policy.maxAttempts(); attempt++) {
            Context ctx = new SimpleContext(executionId, name, attempt, memoryStore);
            try {
                S next = node.invoke(state, ctx, exec).join();
                return NodeOutcome.success(next, attempt);
            } catch (CompletionException ce) {
                Throwable cause = ce.getCause() != null ? ce.getCause() : ce;
                if (cause instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    return NodeOutcome.failure(new NodeExecutionException(name, cause));
                }
                last = cause;
                if (!policy.shouldRetry(attempt, cause)) {
                    return NodeOutcome.failure(new NodeExecutionException(name, cause));
                }
                listener.onRetry(name, attempt, cause);
                traceRecorder.recordRetry(executionId, name, attempt, cause);
                Duration delay = policy.backoff().delayFor(attempt);
                try {
                    sleeper.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return NodeOutcome.failure(new NodeExecutionException(name, ie));
                }
            } catch (Throwable t) {
                last = t;
                if (!policy.shouldRetry(attempt, t)) {
                    return NodeOutcome.failure(new NodeExecutionException(name, t));
                }
                listener.onRetry(name, attempt, t);
                traceRecorder.recordRetry(executionId, name, attempt, t);
                Duration delay = policy.backoff().delayFor(attempt);
                try {
                    sleeper.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return NodeOutcome.failure(new NodeExecutionException(name, ie));
                }
            }
        }
        return NodeOutcome.failure(new NodeExecutionException(name, last));
    }

    public static String newExecutionId() {
        return UUID.randomUUID().toString();
    }

    private record NodeOutcome<S>(S state, int attempts, NodeExecutionException failure) {
        static <S> NodeOutcome<S> success(S state, int attempts) { return new NodeOutcome<>(state, attempts, null); }
        static <S> NodeOutcome<S> failure(NodeExecutionException ex) { return new NodeOutcome<>(null, 0, ex); }
    }

    private record SimpleContext(String executionId, String nodeName, int attempt, MemoryStore memory)
            implements Context {
        @Override
        public Logger logger() {
            return LoggerFactory.getLogger("io.tracegraph.node." + nodeName);
        }
    }
}
