package io.tracegraph.bench;

import io.tracegraph.connectors.llm.ChatMessage;
import io.tracegraph.connectors.llm.LlmRequest;
import io.tracegraph.connectors.llm.MockLlmClient;
import io.tracegraph.connectors.llm.ReActAgent;
import io.tracegraph.core.Graph;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.List;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class ReActBenchmark {

    private Graph<String> reActGraph;

    @Setup
    public void setup() {
        MockLlmClient llm = MockLlmClient.constant("Final answer: done");

        reActGraph = ReActAgent.<String>builder()
                .client(llm)
                .requestFactory(state -> LlmRequest.builder()
                        .model("mock")
                        .messages(List.of(ChatMessage.user(state))))
                .responseFolder((state, response) -> response.content())
                .toolResultFolder((state, results) -> state)
                .build()
                .buildGraph();
    }

    @Benchmark
    public Object reActStep() {
        return reActGraph.run("What is 1+1?");
    }
}
