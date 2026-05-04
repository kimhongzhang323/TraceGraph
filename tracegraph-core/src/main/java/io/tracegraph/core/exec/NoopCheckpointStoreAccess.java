package io.tracegraph.core.exec;

import io.tracegraph.core.Checkpoint;
import io.tracegraph.core.spi.CheckpointStore;

import java.util.Optional;

public final class NoopCheckpointStoreAccess {

    private static final CheckpointStore INSTANCE = new NoopCheckpointStore();

    private NoopCheckpointStoreAccess() {}

    public static CheckpointStore instance() {
        return INSTANCE;
    }

    private static final class NoopCheckpointStore implements CheckpointStore {
        @Override public void save(Checkpoint<?> checkpoint) {}
        @Override public Optional<Checkpoint<?>> latest(String executionId) { return Optional.empty(); }
        @Override public void delete(String executionId) {}
    }
}
