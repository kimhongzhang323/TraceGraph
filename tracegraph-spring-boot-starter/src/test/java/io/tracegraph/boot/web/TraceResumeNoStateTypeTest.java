package io.tracegraph.boot.web;

import io.tracegraph.core.Checkpoint;
import io.tracegraph.core.Graph;
import io.tracegraph.core.spi.CheckpointStore;
import io.tracegraph.observability.replay.InMemoryTraceStore;
import io.tracegraph.observability.replay.RecordingTraceRecorder;
import io.tracegraph.observability.replay.TraceStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = TraceResumeNoStateTypeTest.TestApp.class)
@AutoConfigureMockMvc
class TraceResumeNoStateTypeTest {

    @Autowired MockMvc mockMvc;
    @Autowired Graph<String> graph;

    @Test
    void resumeWithBodyButNoStateTypeConfiguredReturns400() throws Exception {
        graph.run("", "eid-notype-400");

        // Graph has no stateType configured — cannot deserialize body → 400
        mockMvc.perform(post("/tracegraph/traces/eid-notype-400/resume")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("\"EDITED\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApp {

        @Bean
        TraceStore traceStore() {
            return new InMemoryTraceStore();
        }

        @Bean
        CheckpointStore checkpointStore() {
            return new SimpleCheckpointStore();
        }

        @Bean
        Graph<String> stringGraphNoType(TraceStore store, CheckpointStore cpStore) {
            // No .stateType(...) call — state-edit endpoint should refuse body with 400
            return Graph.<String>builder()
                    .node("a", (s, ctx) -> s + "A")
                    .node("b", (s, ctx) -> s + "B")
                    .edge("a", "b").entry("a").terminal("b")
                    .interruptBefore("b")
                    .checkpointStore(cpStore)
                    .traceRecorder(new RecordingTraceRecorder(store))
                    .build();
        }
    }

    static class SimpleCheckpointStore implements CheckpointStore {
        private final Map<String, Checkpoint<?>> store = new ConcurrentHashMap<>();

        @Override
        public void save(Checkpoint<?> checkpoint) {
            store.put(checkpoint.executionId(), checkpoint);
        }

        @Override
        public Optional<Checkpoint<?>> latest(String executionId) {
            return Optional.ofNullable(store.get(executionId));
        }

        @Override
        public void delete(String executionId) {
            store.remove(executionId);
        }
    }
}
