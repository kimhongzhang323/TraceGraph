package io.tracegraph.boot.web;

import io.tracegraph.boot.TraceGraphAutoConfiguration;
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
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = TraceControllerTest.TestApp.class)
@AutoConfigureMockMvc
class TraceControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired TraceStore store;

    @Test
    void returnsTraceJsonForKnownId() throws Exception {
        Graph<String> g = Graph.<String>builder()
                .node("a", (s, ctx) -> s + ".a")
                .node("b", (s, ctx) -> s + ".b")
                .entry("a").edge("a", "b").terminal("b")
                .traceRecorder(new RecordingTraceRecorder(store))
                .build();
        ExecutionResult<String> r = g.run("seed");

        mockMvc.perform(get("/tracegraph/traces/" + r.executionId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionId").value(r.executionId()))
                .andExpect(jsonPath("$.finalState").value("seed.a.b"))
                .andExpect(jsonPath("$.steps.length()").value(2))
                .andExpect(jsonPath("$.steps[0].nodeName").value("a"))
                .andExpect(jsonPath("$.steps[1].nodeName").value("b"));
    }

    @Test
    void returnsNotFoundForUnknownId() throws Exception {
        mockMvc.perform(get("/tracegraph/traces/does-not-exist"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRemovesTrace() throws Exception {
        Graph<String> g = Graph.<String>builder()
                .node("n", (s, ctx) -> s + ".n").entry("n").terminal("n")
                .traceRecorder(new RecordingTraceRecorder(store))
                .build();
        ExecutionResult<String> r = g.run("seed");

        mockMvc.perform(delete("/tracegraph/traces/" + r.executionId()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/tracegraph/traces/" + r.executionId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteReturnsNotFoundForUnknownId() throws Exception {
        mockMvc.perform(delete("/tracegraph/traces/never-existed"))
                .andExpect(status().isNotFound());
    }

    @Test
    void diffReturnsTraceDiffJson() throws Exception {
        Graph<String> g1 = Graph.<String>builder()
                .node("a", (s, ctx) -> s + ".a")
                .node("b", (s, ctx) -> s + ".b")
                .entry("a").edge("a", "b").terminal("b")
                .traceRecorder(new RecordingTraceRecorder(store))
                .build();
        Graph<String> g2 = Graph.<String>builder()
                .node("a", (s, ctx) -> s + ".a")
                .node("c", (s, ctx) -> s + ".c")
                .entry("a").edge("a", "c").terminal("c")
                .traceRecorder(new RecordingTraceRecorder(store))
                .build();
        ExecutionResult<String> r1 = g1.run("seed");
        ExecutionResult<String> r2 = g2.run("seed");

        mockMvc.perform(get("/tracegraph/traces/" + r1.executionId() + "/diff/" + r2.executionId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leftExecutionId").value(r1.executionId()))
                .andExpect(jsonPath("$.rightExecutionId").value(r2.executionId()))
                .andExpect(jsonPath("$.divergenceIndex").value(1))
                .andExpect(jsonPath("$.commonPrefix.length()").value(1))
                .andExpect(jsonPath("$.leftRemainder[0].nodeName").value("b"))
                .andExpect(jsonPath("$.rightRemainder[0].nodeName").value("c"))
                .andExpect(jsonPath("$.sameFinalState").value(false));
    }

    @Test
    void diffReturnsNotFoundIfEitherTraceMissing() throws Exception {
        Graph<String> g = Graph.<String>builder()
                .node("n", (s, ctx) -> s + ".n").entry("n").terminal("n")
                .traceRecorder(new RecordingTraceRecorder(store))
                .build();
        ExecutionResult<String> r = g.run("seed");

        mockMvc.perform(get("/tracegraph/traces/" + r.executionId() + "/diff/missing"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/tracegraph/traces/missing/diff/" + r.executionId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void listRespectsLimitAndOffsetWithTotalCountHeader() throws Exception {
        Graph<String> g = Graph.<String>builder()
                .node("n", (s, ctx) -> s + ".n").entry("n").terminal("n")
                .traceRecorder(new RecordingTraceRecorder(store))
                .build();
        for (int i = 0; i < 5; i++) g.run("seed-" + i);

        int total = store.listIds().size();
        mockMvc.perform(get("/tracegraph/traces").param("limit", "2").param("offset", "1"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", Integer.toString(total)))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void listRejectsNegativePagination() throws Exception {
        mockMvc.perform(get("/tracegraph/traces").param("offset", "-1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/tracegraph/traces").param("limit", "-5"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listsExecutionIds() throws Exception {
        Graph<String> g = Graph.<String>builder()
                .node("n", (s, ctx) -> s + ".n").entry("n").terminal("n")
                .traceRecorder(new RecordingTraceRecorder(store))
                .build();
        ExecutionResult<String> r1 = g.run("a");
        ExecutionResult<String> r2 = g.run("b");

        mockMvc.perform(get("/tracegraph/traces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@=='" + r1.executionId() + "')]").exists())
                .andExpect(jsonPath("$[?(@=='" + r2.executionId() + "')]").exists());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApp {
        @Bean
        TraceStore traceStore() {
            return new InMemoryTraceStore();
        }
    }
}
