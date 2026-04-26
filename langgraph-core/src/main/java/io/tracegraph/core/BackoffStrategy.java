package io.tracegraph.core;

import java.time.Duration;

@FunctionalInterface
public interface BackoffStrategy {

    Duration delayFor(int attempt);
}
