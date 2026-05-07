package io.tracegraph.demo;

import io.tracegraph.core.Graph;
import io.tracegraph.observability.replay.InMemoryTraceStore;
import io.tracegraph.observability.replay.RecordingTraceRecorder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DemoGraphConfig {

    @Bean
    public InMemoryTraceStore traceStore() {
        return new InMemoryTraceStore();
    }

    @Bean
    public Graph<OrderState> graph(InMemoryTraceStore traceStore) {
        return Graph.<OrderState>builder()
                .node("validate", OrderNodes::validate)
                .node("enrich", OrderNodes::enrich)
                .node("score", OrderNodes::score)
                .node("charge", OrderNodes::charge)
                .node("ship", OrderNodes::ship)
                .entry("validate")
                .edge("validate", "enrich")
                .edge("enrich", "score")
                .edge("score", "charge", s -> s.riskScore() < 0.6)
                .edge("charge", "ship")
                .terminal("ship")
                .traceRecorder(new RecordingTraceRecorder(traceStore))
                .build();
    }
}
