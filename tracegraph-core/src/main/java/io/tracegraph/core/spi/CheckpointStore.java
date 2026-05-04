package io.tracegraph.core.spi;

import io.tracegraph.core.Checkpoint;
import io.tracegraph.core.exec.NoopCheckpointStoreAccess;

import java.util.Optional;

/**
 * Persistence SPI for resumable executions. The executor writes a {@link Checkpoint} after each
 * node exit (before edge resolution); {@code Graph.resume(executionId)} re-loads the latest entry
 * and continues from the saved {@code lastCompletedNode}. Nodes are at-least-once on resume —
 * a mid-node crash re-runs that node from attempt 1.
 *
 * <p>Plug in via {@code Graph.Builder.checkpointStore(...)}; default is {@link #noop()}.
 * Implementations must be thread-safe. {@code tracegraph-runtime} ships in-memory and JDBC impls.
 */
public interface CheckpointStore {

    void save(Checkpoint<?> checkpoint);

    Optional<Checkpoint<?>> latest(String executionId);

    void delete(String executionId);

    static CheckpointStore noop() {
        return NoopCheckpointStoreAccess.instance();
    }
}
