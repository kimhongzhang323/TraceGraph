package io.tracegraph.boot.web;

import io.tracegraph.core.Status;
import io.tracegraph.observability.live.LiveTraceFeed;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

@SpringBootTest(classes = TraceLiveControllerTest.TestApp.class)
@AutoConfigureMockMvc
class TraceLiveControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired LiveTraceFeed feed;

    @Test
    void streamsLiveEventsForAllExecutions() throws Exception {
        MvcResult async = mockMvc.perform(get("/tracegraph/stream")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        feed.recordStart("exec-1", "init");
        feed.recordEnter("exec-1", "validate", 1, "init");
        feed.recordComplete("exec-1", Status.COMPLETED, "done");

        String body = async.getResponse().getContentAsString();
        assertThat(body).contains("event:START").contains("event:ENTER").contains("event:COMPLETE");
        assertThat(body).contains("exec-1").contains("validate");
    }

    @Test
    void executionParameterFiltersTheStream() throws Exception {
        MvcResult async = mockMvc.perform(get("/tracegraph/stream").param("execution", "wanted")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        feed.recordStart("other", "x");
        feed.recordStart("wanted", "y");

        String body = async.getResponse().getContentAsString();
        assertThat(body).contains("wanted").doesNotContain("other");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApp {
        @Bean
        TraceStore traceStore() {
            return new InMemoryTraceStore();
        }

        @Bean
        LiveTraceFeed liveTraceFeed() {
            return new LiveTraceFeed();
        }
    }
}
