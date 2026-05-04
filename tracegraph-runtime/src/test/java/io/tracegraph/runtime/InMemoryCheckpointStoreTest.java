package io.tracegraph.runtime;

import io.tracegraph.core.Checkpoint;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryCheckpointStoreTest {

    @Test
    void saveThenLatestReturnsIt() {
        InMemoryCheckpointStore store = new InMemoryCheckpointStore();
        Checkpoint<String> cp = new Checkpoint<>("e1", "n", "state", Instant.EPOCH, false);

        store.save(cp);

        assertThat(store.latest("e1")).contains(cp);
    }

    @Test
    void saveOverwritesPriorCheckpointForSameExecution() {
        InMemoryCheckpointStore store = new InMemoryCheckpointStore();
        Checkpoint<String> first = new Checkpoint<>("e1", "a", "v1", Instant.EPOCH, false);
        Checkpoint<String> second = new Checkpoint<>("e1", "b", "v2", Instant.EPOCH.plusSeconds(1), false);

        store.save(first);
        store.save(second);

        assertThat(store.latest("e1")).contains(second);
        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    void deleteRemovesEntry() {
        InMemoryCheckpointStore store = new InMemoryCheckpointStore();
        store.save(new Checkpoint<>("e1", "n", "s", Instant.EPOCH, false));

        store.delete("e1");

        assertThat(store.latest("e1")).isEmpty();
    }

    @Test
    void latestForUnknownIdIsEmpty() {
        assertThat(new InMemoryCheckpointStore().latest("missing")).isEmpty();
    }

    @Test
    void concurrentSavesAreThreadSafe() throws Exception {
        InMemoryCheckpointStore store = new InMemoryCheckpointStore();
        int threads = 16;
        int perThread = 200;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger errors = new AtomicInteger();

        for (int t = 0; t < threads; t++) {
            final int tid = t;
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        String id = "exec-" + tid + "-" + i;
                        store.save(new Checkpoint<>(id, "n", i, Instant.EPOCH, false));
                        if (store.latest(id).isEmpty()) errors.incrementAndGet();
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(errors).hasValue(0);
        assertThat(store.size()).isEqualTo(threads * perThread);
    }
}
