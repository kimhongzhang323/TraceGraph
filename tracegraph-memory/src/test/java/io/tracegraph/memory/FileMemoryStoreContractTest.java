package io.tracegraph.memory;

import io.tracegraph.core.spi.MemoryStore;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

class FileMemoryStoreContractTest extends MemoryStoreContractTest {

    @TempDir
    Path tempDir;

    @Override
    protected MemoryStore createStore() {
        return FileMemoryStore.of(tempDir);
    }
}
