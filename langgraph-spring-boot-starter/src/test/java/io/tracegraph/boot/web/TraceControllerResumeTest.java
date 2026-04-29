package io.tracegraph.boot.web;

import io.tracegraph.core.Checkpoint;
import io.tracegraph.core.ExecutionResult;
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
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = TraceControllerResumeTest.TestApp.class)
@AutoConfigureMockMvc
class TraceControllerResumeTest {

    @Autowired MockMvc mockMvc;
    @Autowired TraceStore traceStore;
    @Autowired Graph<String> graph;

    @Test
    void resumeReturns200WhenInterrupted() throws Exception {
        graph.run("", "eid-resume-1");

        mockMvc.perform(post("/tracegraph/traces/eid-resume-1/resume"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionId").value("eid-resume-1"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void resumeReturns404ForUnknownTrace() throws Exception {
        mockMvc.perform(post("/tracegraph/traces/ghost-never-ran/resume"))
                .andExpect(status().isNotFound());
    }

    @Test
    void resumeReturns409WhenAlreadyComplete() throws Exception {
        Graph<String> simple = Graph.<String>builder()
                .node("n", (s, ctx) -> s + "N").entry("n").terminal("n")
                .traceRecorder(new RecordingTraceRecorder(traceStore))
                .build();
        simple.run("seed", "eid-complete-409");

        mockMvc.perform(post("/tracegraph/traces/eid-complete-409/resume"))
                .andExpect(status().isConflict());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApp {
        @Bean
        TraceStore traceStore() {
            return new InMemoryTraceStore();
        }

        @Bean
        CheckpointStore simpleCheckpointStore() {
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
