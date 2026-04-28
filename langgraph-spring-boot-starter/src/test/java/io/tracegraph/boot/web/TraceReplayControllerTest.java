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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = TraceReplayControllerTest.TestApp.class)
@AutoConfigureMockMvc
class TraceReplayControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired TraceStore store;
    @Autowired Graph<String> graph;

    @Test
    void replayFromSecondStepReturnsForkedExecution() throws Exception {
        ExecutionResult<String> r = graph.run("seed");

        mockMvc.perform(post("/tracegraph/traces/" + r.executionId() + "/replay").param("step", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionId").exists())
                .andExpect(jsonPath("$.forkedFromExecutionId").value(r.executionId()))
                .andExpect(jsonPath("$.forkedFromStepIndex").value(1))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void replayFromEntryDefaultsToMinusOne() throws Exception {
        ExecutionResult<String> r = graph.run("seed");

        mockMvc.perform(post("/tracegraph/traces/" + r.executionId() + "/replay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.forkedFromStepIndex").value(-1));
    }

    @Test
    void replayReturnsNotFoundForUnknownTrace() throws Exception {
        mockMvc.perform(post("/tracegraph/traces/missing/replay"))
                .andExpect(status().isNotFound());
    }

    @Test
    void replayRejectsOutOfRangeStep() throws Exception {
        ExecutionResult<String> r = graph.run("seed");

        mockMvc.perform(post("/tracegraph/traces/" + r.executionId() + "/replay").param("step", "99"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/tracegraph/traces/" + r.executionId() + "/replay").param("step", "-2"))
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
        Graph<String> stringGraph(TraceStore store) {
            return Graph.<String>builder()
                    .node("a", (s, ctx) -> s + ".a")
                    .node("b", (s, ctx) -> s + ".b")
                    .entry("a").edge("a", "b").terminal("b")
                    .traceRecorder(new RecordingTraceRecorder(store))
                    .build();
        }
    }
}
