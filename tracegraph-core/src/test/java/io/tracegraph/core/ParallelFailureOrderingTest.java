package io.tracegraph.core;

import io.tracegraph.core.exec.NodeExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ParallelFailureOrderingTest {

    @Test
    @Timeout(5)
    void firstDeclaredBranchFailureWinsOverFasterBranchFailure() {
        Graph<String> graph = Graph.<String>builder()
                .parallel("p", List.of(
                        (s, ctx) -> {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            throw new RuntimeException("branch-zero");
                        },
                        (s, ctx) -> { throw new RuntimeException("branch-one"); }
                ), (input, results) -> input)
                .entry("p")
                .terminal("p")
                .build();

        ExecutionResult<String> result = graph.run("seed");

        assertThat(result.status()).isEqualTo(Status.FAILED);
        assertThat(result.error()).isInstanceOf(NodeExecutionException.class);
        assertThat(result.error().getCause()).hasMessage("branch-zero");
    }
}
