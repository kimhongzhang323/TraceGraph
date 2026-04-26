package io.tracegraph.core.exec;

import io.tracegraph.core.Context;
import io.tracegraph.core.Edge;
import io.tracegraph.core.ExecutionResult;
import io.tracegraph.core.Node;
import io.tracegraph.core.RetryPolicy;
import io.tracegraph.core.Status;
import io.tracegraph.core.spi.NodeListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Executor<S> {

    private static final Logger LOG = LoggerFactory.getLogger(Executor.class);

    private final Map<String, Node<S>> nodes;
    private final Map<String, List<Edge<S>>> edgesByFrom;
    private final java.util.Set<String> terminals;
    private final String entry;
    private final NodeListener listener;
    private final int maxSteps;
    private final Map<String, RetryPolicy> nodePolicies;
    private final RetryPolicy defaultPolicy;
    private final Sleeper sleeper;

    public Executor(Map<String, Node<S>> nodes,
                    Map<String, List<Edge<S>>> edgesByFrom,
                    java.util.Set<String> terminals,
                    String entry,
                    NodeListener listener,
                    int maxSteps,
                    Map<String, RetryPolicy> nodePolicies,
                    RetryPolicy defaultPolicy) {
        this(nodes, edgesByFrom, terminals, entry, listener, maxSteps, nodePolicies, defaultPolicy, Sleeper.realtime());
    }

    Executor(Map<String, Node<S>> nodes,
             Map<String, List<Edge<S>>> edgesByFrom,
             java.util.Set<String> terminals,
             String entry,
             NodeListener listener,
             int maxSteps,
             Map<String, RetryPolicy> nodePolicies,
             RetryPolicy defaultPolicy,
             Sleeper sleeper) {
        this.nodes = nodes;
        this.edgesByFrom = edgesByFrom;
        this.terminals = terminals;
        this.entry = entry;
        this.listener = listener;
        this.maxSteps = maxSteps;
        this.nodePolicies = nodePolicies;
        this.defaultPolicy = defaultPolicy;
        this.sleeper = sleeper;
    }

    public ExecutionResult<S> run(S initial) {
        String executionId = UUID.randomUUID().toString();
        List<String> path = new ArrayList<>();
        S state = initial;
        String current = entry;
        int steps = 0;

        while (current != null) {
            if (steps++ >= maxSteps) {
                LOG.warn("[{}] max-step guard ({}) reached at node '{}'", executionId, maxSteps, current);
                return new ExecutionResult<>(state, path, Status.HALTED, null);
            }

            Node<S> node = nodes.get(current);
            RetryPolicy policy = nodePolicies.getOrDefault(current, defaultPolicy);
            path.add(current);

            listener.onEnter(current, state);
            NodeOutcome<S> outcome = invokeWithRetry(node, state, current, executionId, policy);
            if (outcome.failure != null) {
                listener.onError(current, outcome.failure.getCause() != null ? outcome.failure.getCause() : outcome.failure);
                return new ExecutionResult<>(state, path, Status.FAILED, outcome.failure);
            }
            state = outcome.state;
            listener.onExit(current, state);

            if (terminals.contains(current)) {
                return new ExecutionResult<>(state, path, Status.COMPLETED, null);
            }

            String next = null;
            for (Edge<S> edge : edgesByFrom.getOrDefault(current, List.of())) {
                if (edge.matches(state)) {
                    next = edge.to();
                    break;
                }
            }

            if (next == null) {
                return new ExecutionResult<>(state, path, Status.COMPLETED, null);
            }
            current = next;
        }

        return new ExecutionResult<>(state, path, Status.COMPLETED, null);
    }

    private NodeOutcome<S> invokeWithRetry(Node<S> node, S state, String name, String executionId, RetryPolicy policy) {
        Throwable last = null;
        for (int attempt = 1; attempt <= policy.maxAttempts(); attempt++) {
            Context ctx = new SimpleContext(executionId, name, attempt);
            try {
                S next = node.execute(state, ctx);
                return NodeOutcome.success(next);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return NodeOutcome.failure(new NodeExecutionException(name, ie));
            } catch (Throwable t) {
                last = t;
                if (!policy.shouldRetry(attempt, t)) {
                    return NodeOutcome.failure(new NodeExecutionException(name, t));
                }
                listener.onRetry(name, attempt, t);
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

    private record NodeOutcome<S>(S state, NodeExecutionException failure) {
        static <S> NodeOutcome<S> success(S state) { return new NodeOutcome<>(state, null); }
        static <S> NodeOutcome<S> failure(NodeExecutionException ex) { return new NodeOutcome<>(null, ex); }
    }

    private record SimpleContext(String executionId, String nodeName, int attempt) implements Context {
        @Override
        public Logger logger() {
            return LoggerFactory.getLogger("io.tracegraph.node." + nodeName);
        }
    }
}
