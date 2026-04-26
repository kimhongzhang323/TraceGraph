package io.tracegraph.core.spi;

import io.tracegraph.core.Checkpoint;
import io.tracegraph.core.exec.NoopCheckpointStoreAccess;

import java.util.Optional;

public interface CheckpointStore {

    void save(Checkpoint<?> checkpoint);

    Optional<Checkpoint<?>> latest(String executionId);

    void delete(String executionId);

    static CheckpointStore noop() {
        return NoopCheckpointStoreAccess.instance();
    }
}
