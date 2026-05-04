package io.tracegraph.examples.boot;

import io.tracegraph.core.Graph;
import io.tracegraph.core.spi.NodeListener;
import io.tracegraph.core.spi.TraceRecorder;
import io.tracegraph.observability.replay.InMemoryTraceStore;
import io.tracegraph.observability.replay.TraceStore;
import io.tracegraph.observability.replay.RecordingTraceRecorder;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfiguration {

    record AgentState(String input, String output) {}

    @Bean
    public TraceStore traceStore() {
        return new InMemoryTraceStore();
    }

    @Bean
    public TraceRecorder traceRecorder(TraceStore traceStore) {
        return new RecordingTraceRecorder(traceStore);
    }

    @Bean
    public Graph<AgentState> graph(TraceRecorder traceRecorder, NodeListener listener) {
        return Graph.<AgentState>builder()
                .node("process", (state, ctx) ->
                        new AgentState(state.input(), "Processed: " + state.input()))
                .node("format", (state, ctx) ->
                        new AgentState(state.input(), "[" + state.output() + "]"))
                .entry("process")
                .edge("process", "format")
                .listener(listener)
                .traceRecorder(traceRecorder)
                .build();
    }

    @Bean
    public CommandLineRunner demo(Graph<AgentState> graph, TraceStore traceStore) {
        return args -> {
            for (String input : new String[]{"hello", "world", "TraceGraph"}) {
                var result = graph.run(new AgentState(input, null));
                System.out.println("Ran execution " + result.executionId() + ": " + result.finalState().output());
            }
            System.out.println("Stored traces: " + traceStore.listIds());
            System.out.println("REST API available at http://localhost:8080/tracegraph/traces");
        };
    }
}
