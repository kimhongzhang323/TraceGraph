package io.tracegraph.core;

import io.tracegraph.core.spi.MemoryStore;
import org.slf4j.Logger;

public interface Context {
    String executionId();

    String nodeName();

    int attempt();

    Logger logger();

    default String idempotencyKey() {
        return executionId() + ":" + nodeName() + ":" + attempt();
    }

    default MemoryStore memory() {
        return MemoryStore.noop();
    }
}
