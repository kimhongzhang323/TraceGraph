package io.tracegraph.core;

import org.slf4j.Logger;

public interface Context {
    String executionId();

    String nodeName();

    int attempt();

    Logger logger();

    default String idempotencyKey() {
        return executionId() + ":" + nodeName() + ":" + attempt();
    }
}
