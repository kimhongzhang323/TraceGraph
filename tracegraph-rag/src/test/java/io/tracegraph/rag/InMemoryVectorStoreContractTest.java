package io.tracegraph.rag;

import io.tracegraph.core.spi.VectorStore;

class InMemoryVectorStoreContractTest extends VectorStoreContractTest {

    @Override
    protected VectorStore createStore() {
        return new InMemoryVectorStore();
    }
}
