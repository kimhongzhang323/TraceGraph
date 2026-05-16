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

@SpringBootTest(classes = TraceResumeStateEditTest.TestApp.class)
@AutoConfigureMockMvc
class TraceResumeStateEditTest {

    @Autowired MockMvc mockMvc;
    @Autowired Graph<String> graph;

    @Test
    void resumeWithoutBodyBackwardCompat() throws Exception {
        graph.run("", "eid-sc-compat");

        mockMvc.perform(post("/tracegraph/traces/eid-sc-compat/resume"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionId").value("eid-sc-compat"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void resumeWithStateBodyOverridesState() throws Exception {
        graph.run("", "eid-sc-override");

        // State after "a" is "A". Override to "EDITED" — "b" appends "B" → "EDITEDB".
        mockMvc.perform(post("/tracegraph/traces/eid-sc-override/resume")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("\"EDITED\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionId").value("eid-sc-override"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.finalState").value("EDITEDB"));
    }

    @Test
    void resumeWithStateBodyReturns404ForUnknownTrace() throws Exception {
        mockMvc.perform(post("/tracegraph/traces/ghost-sc-404/resume")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("\"EDITED\""))
                .andExpect(status().isNotFound());
    }

    @Test
    void resumeWithUndeserializableBodyReturns400() throws Exception {
        graph.run("", "eid-sc-badjson");

        // JSON object cannot be deserialized to String (the graph's state type) → 400
        mockMvc.perform(post("/tracegraph/traces/eid-sc-badjson/resume")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"not\":\"a-string\"}"))
                .andExpect(status().isBadRequest());
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
        Graph<String> stringGraph(TraceStore store, CheckpointStore cpStore) {
            return Graph.<String>builder()
                    .node("a", (s, ctx) -> s + "A")
                    .node("b", (s, ctx) -> s + "B")
                    .edge("a", "b").entry("a").terminal("b")
                    .interruptBefore("b")
                    .checkpointStore(cpStore)
                    .stateType(String.class)
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
