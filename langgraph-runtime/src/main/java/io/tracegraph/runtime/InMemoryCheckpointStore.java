package io.tracegraph.runtime;

import io.tracegraph.core.Checkpoint;
import io.tracegraph.core.spi.CheckpointStore;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryCheckpointStore implements CheckpointStore {

    private final ConcurrentMap<String, Checkpoint<?>> latest = new ConcurrentHashMap<>();

    @Override
    public void save(Checkpoint<?> checkpoint) {
        Objects.requireNonNull(checkpoint, "checkpoint");
        latest.put(checkpoint.executionId(), checkpoint);
    }

    @Override
    public Optional<Checkpoint<?>> latest(String executionId) {
        Objects.requireNonNull(executionId, "executionId");
        return Optional.ofNullable(latest.get(executionId));
    }

    @Override
    public void delete(String executionId) {
        Objects.requireNonNull(executionId, "executionId");
        latest.remove(executionId);
    }

    public int size() {
        return latest.size();
    }
}
