package io.tracegraph.observability.replay;

class InMemoryTraceStoreContractTest extends TraceStoreContractTest {

    @Override
    protected TraceStore createStore() {
        return new InMemoryTraceStore();
    }
}
