package io.tracegraph.memory;

import io.tracegraph.core.spi.MemoryStore;

class InMemoryMemoryStoreContractTest extends MemoryStoreContractTest {

    @Override
    protected MemoryStore createStore() {
        return new InMemoryMemoryStore();
    }
}
