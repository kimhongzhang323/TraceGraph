package io.tracegraph.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutorOverrideTest {

    @Test
    void userSuppliedExecutorIsHonoredAndNotShutDown() throws Exception {
        AtomicInteger submissions = new AtomicInteger();
        ExecutorService user = Executors.newFixedThreadPool(4, r -> {
            submissions.incrementAndGet();
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });

        try {
            Graph<String> graph = Graph.<String>builder()
                    .parallel("fan", List.of(
                            (s, ctx) -> "a",
                            (s, ctx) -> "b"
                    ), (input, results) -> results.get(0) + results.get(1))
                    .entry("fan")
                    .terminal("fan")
                    .executor(user)
                    .build();

            ExecutionResult<String> r = graph.run("");

            assertThat(r.status()).isEqualTo(Status.COMPLETED);
            assertThat(submissions).hasValueGreaterThan(0);
            assertThat(user.isShutdown()).isFalse();
        } finally {
            user.shutdownNow();
            user.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void defaultExecutorWorksWithoutOverride() {
        Graph<String> graph = Graph.<String>builder()
                .parallel("fan", List.of(
                        (s, ctx) -> "a",
                        (s, ctx) -> "b",
                        (s, ctx) -> "c"
                ), (input, results) -> String.join("", results))
                .entry("fan")
                .terminal("fan")
                .build();

        ExecutionResult<String> r = graph.run("");
        assertThat(r.finalState()).isEqualTo("abc");
    }
}
