package io.tracegraph.boot.web;

import io.tracegraph.core.Graph;
import io.tracegraph.observability.replay.InMemoryTraceStore;
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
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = TraceStreamControllerTest.TestApp.class)
@AutoConfigureMockMvc
class TraceStreamControllerTest {

    @Autowired MockMvc mockMvc;

    @Test
    void streamEndpointReturnsSseContentType() throws Exception {
        MvcResult async = mockMvc.perform(post("/tracegraph/traces/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("\"seed\""))
                .andExpect(request().asyncStarted())
                .andReturn();

        async.getAsyncResult(5000L);

        String body = mockMvc.perform(asyncDispatch(async))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("NodeEnter");
        assertThat(body).contains("Complete");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApp {
        @Bean
        TraceStore traceStore() {
            return new InMemoryTraceStore();
        }

        @Bean
        Graph<String> stringGraph() {
            return Graph.<String>builder()
                    .node("a", (s, ctx) -> s + ".a")
                    .node("b", (s, ctx) -> s + ".b")
                    .entry("a").edge("a", "b").terminal("b")
                    .build();
        }
    }
}
