package io.tracegraph.core.spi;

public interface NodeListener {

    NodeListener NOOP = new NodeListener() {};

    default void onEnter(String nodeName, Object state) {}

    default void onExit(String nodeName, Object state) {}

    default void onError(String nodeName, Throwable error) {}

    default void onRetry(String nodeName, int attempt, Throwable error) {}
}
