package io.tracegraph.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutorLeakTest {

    @Test
    @Timeout(30)
    void sharedExecutorServiceNotShutDownAfterManyRuns() {
        ExecutorService userExecutor = Executors.newFixedThreadPool(4);

        try {
            long memBefore = Runtime.getRuntime().totalMemory();
            int threadsBefore = Thread.activeCount();

            Graph<String> graph = Graph.<String>builder()
                    .node("step", (s, ctx) -> s + ".")
                    .entry("step")
                    .terminal("step")
                    .executor(userExecutor)
                    .build();

            for (int i = 0; i < 1000; i++) {
                ExecutionResult<String> result = graph.run("run-" + i);
                assertThat(result.status()).isEqualTo(Status.COMPLETED);
            }

            System.gc();
            long memAfter = Runtime.getRuntime().totalMemory();
            int threadsAfter = Thread.activeCount();

            assertThat(userExecutor.isShutdown()).isFalse();
            assertThat(threadsAfter).isLessThan(threadsBefore + 10);
            assertThat(memAfter).isLessThan(memBefore * 2);
        } finally {
            userExecutor.shutdownNow();
        }
    }
}
