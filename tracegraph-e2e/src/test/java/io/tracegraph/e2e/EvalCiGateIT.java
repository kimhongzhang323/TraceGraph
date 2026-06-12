package io.tracegraph.e2e;

import io.tracegraph.connectors.llm.ChatMessage;
import io.tracegraph.connectors.llm.LlmClient;
import io.tracegraph.connectors.llm.LlmRequest;
import io.tracegraph.connectors.llm.LlmResponse;
import io.tracegraph.connectors.llm.RecordedLlmClient;
import io.tracegraph.core.Graph;
import io.tracegraph.eval.EvalCase;
import io.tracegraph.eval.EvalSuite;
import io.tracegraph.eval.golden.EvalRunner;
import io.tracegraph.eval.golden.GoldenEvalResult;
import io.tracegraph.eval.golden.GoldenTrace;
import io.tracegraph.eval.metric.ExactMatchMetric;
import io.tracegraph.observability.replay.InMemoryTraceStore;
import io.tracegraph.observability.replay.RecordingTraceRecorder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The CI eval gate: a deterministic LLM graph (RecordedLlmClient, no network) is held to its
 * golden traces and an exact-match eval suite. Run by the {@code Eval gate} workflow on every
 * PR and main push — a behavior regression in executor, tracing, or replay machinery fails it.
 */
class EvalCiGateIT {

    private static final List<RecordedLlmClient.Exchange> EXCHANGES = List.of(
            exchange("capital of france?", "Paris"),
            exchange("capital of japan?", "Tokyo"),
            exchange("2+2?", "4"));

    private static RecordedLlmClient.Exchange exchange(String question, String answer) {
        return new RecordedLlmClient.Exchange(request(question),
                new LlmResponse(answer, LlmResponse.FinishReason.STOP, LlmResponse.Usage.ZERO));
    }

    private static LlmRequest request(String question) {
        return LlmRequest.builder()
                .model("recorded-model")
                .messages(List.of(ChatMessage.user(question)))
                .build();
    }

    private static Graph<String> qaGraph(InMemoryTraceStore store) {
        LlmClient client = RecordedLlmClient.replaying(EXCHANGES);
        Graph.Builder<String> builder = Graph.<String>builder()
                .node("answer", (question, ctx) -> client.complete(request(question)).content())
                .entry("answer")
                .terminal("answer");
        if (store != null) {
            builder.traceRecorder(new RecordingTraceRecorder(store));
        }
        return builder.build();
    }

    @Test
    void goldenTracesAndExactMatchSuiteGateTheBuild() {
        List<String> questions = List.of("capital of france?", "capital of japan?", "2+2?");

        InMemoryTraceStore goldenStore = new InMemoryTraceStore();
        Graph<String> goldenGraph = qaGraph(goldenStore);
        List<GoldenTrace<String>> goldens = new ArrayList<>();
        for (String question : questions) {
            String id = goldenGraph.run(question).executionId();
            goldens.add(new GoldenTrace<>(load(goldenStore, id)));
        }

        InMemoryTraceStore runStore = new InMemoryTraceStore();
        EvalRunner<String> runner = EvalRunner.<String>builder()
                .graph(qaGraph(runStore))
                .runStore(runStore)
                .assertion("answer", step -> step.attempts() == 1
                        ? Optional.empty()
                        : Optional.of("answer node retried " + step.attempts() + " times"))
                .build();
        List<GoldenEvalResult<String>> results = runner.run(goldens);

        assertThat(results).hasSize(3).allMatch(GoldenEvalResult::passed,
                "every golden re-run must match step-for-step and pass node assertions");

        EvalSuite<String> suite = EvalSuite.builder(qaGraph(null))
                .addCases(List.of(
                        EvalCase.of("france", "capital of france?", "Paris"),
                        EvalCase.of("japan", "capital of japan?", "Tokyo"),
                        EvalCase.of("math", "2+2?", "4")))
                .metric(new ExactMatchMetric<>())
                .minPassRate(1.0)
                .build();
        suite.assertPassed(suite.run());
    }

    @SuppressWarnings("unchecked")
    private static io.tracegraph.observability.replay.ExecutionTrace<String> load(
            InMemoryTraceStore store, String executionId) {
        return (io.tracegraph.observability.replay.ExecutionTrace<String>)
                store.load(executionId).orElseThrow();
    }
}
