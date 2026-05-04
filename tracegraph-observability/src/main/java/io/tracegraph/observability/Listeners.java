package io.tracegraph.observability;

import io.tracegraph.core.spi.NodeListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

public final class Listeners {

    private static final Logger LOG = LoggerFactory.getLogger(Listeners.class);

    private Listeners() {}

    public static NodeListener compose(NodeListener... listeners) {
        Objects.requireNonNull(listeners, "listeners");
        if (listeners.length == 0) return NodeListener.NOOP;
        if (listeners.length == 1) return Objects.requireNonNull(listeners[0], "listener");
        for (NodeListener l : listeners) Objects.requireNonNull(l, "listener");
        return new Composite(List.of(listeners));
    }

    private record Composite(List<NodeListener> delegates) implements NodeListener {

        @Override
        public void onEnter(String nodeName, Object state) {
            for (NodeListener l : delegates) safe(l, "onEnter", () -> l.onEnter(nodeName, state));
        }

        @Override
        public void onExit(String nodeName, Object state) {
            for (NodeListener l : delegates) safe(l, "onExit", () -> l.onExit(nodeName, state));
        }

        @Override
        public void onState(String nodeName, Object before, Object after) {
            for (NodeListener l : delegates) safe(l, "onState", () -> l.onState(nodeName, before, after));
        }

        @Override
        public void onError(String nodeName, Throwable error) {
            for (NodeListener l : delegates) safe(l, "onError", () -> l.onError(nodeName, error));
        }

        @Override
        public void onRetry(String nodeName, int attempt, Throwable error) {
            for (NodeListener l : delegates) safe(l, "onRetry", () -> l.onRetry(nodeName, attempt, error));
        }

        private static void safe(NodeListener l, String hook, Runnable body) {
            try {
                body.run();
            } catch (Throwable t) {
                LOG.warn("Listener {} threw from {}: {}", l.getClass().getName(), hook, t.toString());
            }
        }
    }
}
