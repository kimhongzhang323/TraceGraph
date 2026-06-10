package io.tracegraph.runtime;

import io.tracegraph.core.spi.CheckpointStore;

class InMemoryCheckpointStoreContractTest extends CheckpointStoreContractTest {

    @Override
    protected CheckpointStore createStore() {
        return new InMemoryCheckpointStore();
    }
}
