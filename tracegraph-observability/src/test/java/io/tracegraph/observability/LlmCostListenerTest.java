package io.tracegraph.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.tracegraph.core.ExecutionResult;
import io.tracegraph.core.Graph;
import io.tracegraph.core.Node;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LlmCostListenerTest {

    @Test
    void recordUsageAccumulatesPromptAndCompletionTokens() {
        LlmCostListener listener = new LlmCostListener();
        listener.recordUsage("exec-1", 10, 5);
        listener.recordUsage("exec-1", 20, 15);

        assertThat(listener.promptTokens("exec-1")).isEqualTo(30);
        assertThat(listener.completionTokens("exec-1")).isEqualTo(20);
    }

    @Test
    void recordNodeUsageAccumulatesPerNode() {
        LlmCostListener listener = new LlmCostListener();
        listener.recordNodeUsage("chat", 10, 5);
        listener.recordNodeUsage("chat", 10, 5);
        listener.recordNodeUsage("summarize", 50, 20);

        assertThat(listener.nodePromptTokens("chat")).isEqualTo(20);
        assertThat(listener.nodeCompletionTokens("chat")).isEqualTo(10);
        assertThat(listener.nodePromptTokens("summarize")).isEqualTo(50);
        assertThat(listener.nodeCompletionTokens("summarize")).isEqualTo(20);
    }

    @Test
    void totalTokensIsSumOfPromptAndCompletion() {
        LlmCostListener listener = new LlmCostListener();
        listener.recordUsage("exec-2", 100, 40);

        assertThat(listener.totalTokens("exec-2")).isEqualTo(140);
    }

    @Test
    void onUsageDelegatestoRecordNodeUsage() {
        LlmCostListener listener = new LlmCostListener();
        listener.onUsage("chat", 15, 7);

        assertThat(listener.nodePromptTokens("chat")).isEqualTo(15);
        assertThat(listener.nodeCompletionTokens("chat")).isEqualTo(7);
    }

    @Test
    void metricsEmittedToRegistryOnRecordUsage() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LlmCostListener listener = new LlmCostListener(registry);

        listener.recordUsage("exec-3", 30, 10);

        Counter promptCounter = registry.counter("tracegraph.llm.prompt_tokens", "execution_id", "exec-3");
        Counter completionCounter = registry.counter("tracegraph.llm.completion_tokens", "execution_id", "exec-3");

        assertThat(promptCounter.count()).isEqualTo(30.0);
        assertThat(completionCounter.count()).isEqualTo(10.0);
    }

    @Test
    void metricsEmittedToRegistryOnRecordNodeUsage() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LlmCostListener listener = new LlmCostListener(registry);

        listener.recordNodeUsage("llm-node", 50, 25);

        Counter promptCounter = registry.counter("tracegraph.llm.node.prompt_tokens", "node", "llm-node");
        Counter completionCounter = registry.counter("tracegraph.llm.node.completion_tokens", "node", "llm-node");

        assertThat(promptCounter.count()).isEqualTo(50.0);
        assertThat(completionCounter.count()).isEqualTo(25.0);
    }

    @Test
    void recordUsageWithNodeAccumulatesPerExecutionPerNode() {
        LlmCostListener listener = new LlmCostListener();
        listener.recordUsage("exec-a", "chat", 10, 5);
        listener.recordUsage("exec-a", "summarize", 20, 7);
        listener.recordUsage("exec-a", "chat", 30, 9);
        listener.recordUsage("exec-b", "chat", 100, 50);

        CostReport reportA = listener.snapshot("exec-a");
        assertThat(reportA.executionId()).isEqualTo("exec-a");
        assertThat(reportA.usageByNode()).containsEntry("chat", new Usage(40, 14));
        assertThat(reportA.usageByNode()).containsEntry("summarize", new Usage(20, 7));
        assertThat(reportA.totalUsage()).isEqualTo(new Usage(60, 21));

        CostReport reportB = listener.snapshot("exec-b");
        assertThat(reportB.usageByNode()).containsExactlyEntriesOf(java.util.Map.of("chat", new Usage(100, 50)));
        assertThat(reportB.totalUsage()).isEqualTo(new Usage(100, 50));
    }

    @Test
    void snapshotForUnknownExecutionReturnsEmpty() {
        LlmCostListener listener = new LlmCostListener();
        CostReport report = listener.snapshot("nope");

        assertThat(report.executionId()).isEqualTo("nope");
        assertThat(report.usageByNode()).isEmpty();
        assertThat(report.totalUsage()).isEqualTo(Usage.ZERO);
    }

    @Test
    void snapshotIsImmutable() {
        LlmCostListener listener = new LlmCostListener();
        listener.recordUsage("exec-x", "n", 1, 1);
        CostReport report = listener.snapshot("exec-x");

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> report.usageByNode().put("other", new Usage(0, 0)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void recordUsageWithNodeUpdatesExecutionTotalButNotNodeTotal() {
        // The 4-arg recordUsage updates execution + per-execution-per-node buckets only.
        // The node-global bucket is owned by onUsage (NodeListener) so wiring the listener
        // as both NodeListener and TraceRecorder doesn't double-count.
        LlmCostListener listener = new LlmCostListener();
        listener.recordUsage("exec-q", "chat", 7, 3);

        assertThat(listener.promptTokens("exec-q")).isEqualTo(7);
        assertThat(listener.completionTokens("exec-q")).isEqualTo(3);
        assertThat(listener.nodePromptTokens("chat")).isEqualTo(0);
        assertThat(listener.nodeCompletionTokens("chat")).isEqualTo(0);
    }

    @Test
    void traceRecorderRecordUsagePopulatesSnapshot() {
        LlmCostListener listener = new LlmCostListener();
        // Wired as TraceRecorder, the listener receives executionId-aware usage events.
        listener.recordUsage("exec-tr", "nodeA", 11, 13);
        listener.recordUsage("exec-tr", "nodeB", 2, 4);

        CostReport report = listener.snapshot("exec-tr");
        assertThat(report.usageByNode()).containsKeys("nodeA", "nodeB");
        assertThat(report.totalUsage()).isEqualTo(new Usage(13, 17));
    }

    @Test
    void usageByNodeAndTotalUsageQueryablePerExecution() {
        LlmCostListener listener = new LlmCostListener();
        listener.recordUsage("e1", "a", 1, 1);
        listener.recordUsage("e1", "b", 2, 2);
        listener.recordUsage("e2", "a", 100, 100);

        assertThat(listener.usageByNode("e1"))
                .containsExactlyInAnyOrderEntriesOf(java.util.Map.of(
                        "a", new Usage(1, 1), "b", new Usage(2, 2)));
        assertThat(listener.totalUsage("e1")).isEqualTo(new Usage(3, 3));
        assertThat(listener.totalUsage("e2")).isEqualTo(new Usage(100, 100));
    }

    @Test
    void perExecutionPerNodeUsageViaGraph() {
        LlmCostListener listener = new LlmCostListener();

        Node<String> nodeA = (state, ctx) -> {
            ctx.reportUsage(10, 5);
            return state + "-A";
        };
        Node<String> nodeB = (state, ctx) -> {
            ctx.reportUsage(20, 8);
            return state + "-B";
        };

        Graph<String> g = Graph.<String>builder()
                .listener(listener)
                .traceRecorder(listener)
                .entry("nodeA")
                .node("nodeA", nodeA)
                .node("nodeB", nodeB)
                .edge("nodeA", "nodeB")
                .terminal("nodeB")
                .build();

        ExecutionResult<String> r1 = g.run("seed", "exec-graph-1");
        ExecutionResult<String> r2 = g.run("seed", "exec-graph-2");
        assertThat(r1.finalState()).isEqualTo("seed-A-B");
        assertThat(r2.finalState()).isEqualTo("seed-A-B");

        CostReport report1 = listener.snapshot("exec-graph-1");
        assertThat(report1.usageByNode()).containsEntry("nodeA", new Usage(10, 5));
        assertThat(report1.usageByNode()).containsEntry("nodeB", new Usage(20, 8));
        assertThat(report1.totalUsage()).isEqualTo(new Usage(30, 13));

        CostReport report2 = listener.snapshot("exec-graph-2");
        assertThat(report2.usageByNode()).containsEntry("nodeA", new Usage(10, 5));
        assertThat(report2.usageByNode()).containsEntry("nodeB", new Usage(20, 8));
        assertThat(report2.totalUsage()).isEqualTo(new Usage(30, 13));
    }
}
