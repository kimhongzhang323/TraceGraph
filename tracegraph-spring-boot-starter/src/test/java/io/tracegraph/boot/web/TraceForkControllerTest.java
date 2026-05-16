package io.tracegraph.boot.web;

import io.tracegraph.core.ExecutionResult;
import io.tracegraph.core.Graph;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = TraceForkControllerTest.TestApp.class)
@AutoConfigureMockMvc
class TraceForkControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired Graph<String> graph;
    @Autowired TraceStore store;

    @Test
    void forkFromStepReturnsNewExecutionId() throws Exception {
        ExecutionResult<String> r = graph.run("seed");

        mockMvc.perform(post("/tracegraph/traces/" + r.executionId() + "/fork").param("step", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionId").exists())
                .andExpect(jsonPath("$.forkedFromExecutionId").value(r.executionId()))
                .andExpect(jsonPath("$.forkedFromStepIndex").value(0))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void forkFromEntryDefaultsToMinusOne() throws Exception {
        ExecutionResult<String> r = graph.run("seed");

        mockMvc.perform(post("/tracegraph/traces/" + r.executionId() + "/fork"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.forkedFromStepIndex").value(-1));
    }

    @Test
    void forkReturns404ForUnknownTrace() throws Exception {
        mockMvc.perform(post("/tracegraph/traces/ghost-fork/fork"))
                .andExpect(status().isNotFound());
    }

    @Test
    void forkRejectsOutOfRangeStep() throws Exception {
        ExecutionResult<String> r = graph.run("seed");

        mockMvc.perform(post("/tracegraph/traces/" + r.executionId() + "/fork").param("step", "99"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void forkWithSeedOverrideUsesProvidedState() throws Exception {
        ExecutionResult<String> r = graph.run("seed");

        // Override state at step 0 with "FORKED" — node "b" appends ".b" → "FORKED.b"
        mockMvc.perform(post("/tracegraph/traces/" + r.executionId() + "/fork")
                        .param("step", "0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("\"FORKED\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.finalState").value("FORKED.b"));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApp {

        @Bean
        TraceStore traceStore() {
            return new InMemoryTraceStore();
        }

        @Bean
        Graph<String> stringGraph(TraceStore store) {
            // Has stateType configured for seed-override test
            return Graph.<String>builder()
                    .node("a", (s, ctx) -> s + ".a")
                    .node("b", (s, ctx) -> s + ".b")
                    .entry("a").edge("a", "b").terminal("b")
                    .stateType(String.class)
                    .traceRecorder(new RecordingTraceRecorder(store))
                    .build();
        }
    }
}
