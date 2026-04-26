package io.tracegraph.core.exec;

import io.tracegraph.core.Context;
import io.tracegraph.core.Edge;
import io.tracegraph.core.ExecutionResult;
import io.tracegraph.core.Node;
import io.tracegraph.core.Status;
import io.tracegraph.core.spi.NodeListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    public Executor(Map<String, Node<S>> nodes,
                    Map<String, List<Edge<S>>> edgesByFrom,
                    java.util.Set<String> terminals,
                    String entry,
                    NodeListener listener,
                    int maxSteps) {
        this.nodes = nodes;
        this.edgesByFrom = edgesByFrom;
        this.terminals = terminals;
        this.entry = entry;
        this.listener = listener;
        this.maxSteps = maxSteps;
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
            path.add(current);
            Context ctx = new SimpleContext(executionId, current, 1);

            listener.onEnter(current, state);
            try {
                state = node.execute(state, ctx);
            } catch (Throwable t) {
                listener.onError(current, t);
                NodeExecutionException wrapped = new NodeExecutionException(current, t);
                return new ExecutionResult<>(state, path, Status.FAILED, wrapped);
            }
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

    private record SimpleContext(String executionId, String nodeName, int attempt) implements Context {
        @Override
        public Logger logger() {
            return LoggerFactory.getLogger("io.tracegraph.node." + nodeName);
        }
    }
}
