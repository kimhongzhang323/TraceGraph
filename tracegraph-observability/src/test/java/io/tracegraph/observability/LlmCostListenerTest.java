package io.tracegraph.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
}
