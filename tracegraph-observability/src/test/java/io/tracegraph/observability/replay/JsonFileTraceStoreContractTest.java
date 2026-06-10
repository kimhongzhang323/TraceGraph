package io.tracegraph.observability.replay;

import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

class JsonFileTraceStoreContractTest extends TraceStoreContractTest {

    @TempDir
    Path tempDir;

    @Override
    protected TraceStore createStore() {
        return JsonFileTraceStore.of(tempDir, String.class);
    }
}
