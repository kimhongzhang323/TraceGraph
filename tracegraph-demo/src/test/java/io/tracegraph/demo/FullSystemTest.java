package io.tracegraph.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class FullSystemTest {

    @Autowired MockMvc mockMvc;
    final ObjectMapper objectMapper = new ObjectMapper();

    private String[] listIds() throws Exception {
        MvcResult result = mockMvc.perform(get("/tracegraph/traces"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), String[].class);
    }

    @Test
    void listTracesReturnsSeededEntries() throws Exception {
        mockMvc.perform(get("/tracegraph/traces"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Total-Count"))
                .andExpect(result -> {
                    String[] ids = objectMapper.readValue(
                            result.getResponse().getContentAsString(), String[].class);
                    assertThat(ids).hasSizeGreaterThanOrEqualTo(4);
                });
    }

    @Test
    void getTraceReturnsFullTrace() throws Exception {
        String[] ids = listIds();
        assertThat(ids).isNotEmpty();

        mockMvc.perform(get("/tracegraph/traces/" + ids[0]))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionId").exists());
    }

    @Test
    void getUnknownTraceReturns404() throws Exception {
        mockMvc.perform(get("/tracegraph/traces/nonexistent-id-xyz"))
                .andExpect(status().isNotFound());
    }

    @Test
    void diffTwoTracesReturnsResult() throws Exception {
        String[] ids = listIds();
        assertThat(ids).hasSizeGreaterThanOrEqualTo(2);

        mockMvc.perform(get("/tracegraph/traces/" + ids[0] + "/diff/" + ids[1]))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    assertThat(body).containsAnyOf("divergenceIndex", "leftExecutionId");
                });
    }

    @Test
    void replayTraceCreatesNewExecution() throws Exception {
        String[] ids = listIds();
        assertThat(ids).isNotEmpty();
        String originalId = ids[0];

        mockMvc.perform(post("/tracegraph/traces/" + originalId + "/replay?step=0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.forkedFromExecutionId").value(originalId));
    }

    @Test
    void deleteTraceRemovesIt() throws Exception {
        String[] ids = listIds();
        assertThat(ids).isNotEmpty();
        String lastId = ids[ids.length - 1];

        mockMvc.perform(delete("/tracegraph/traces/" + lastId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/tracegraph/traces/" + lastId))
                .andExpect(status().isNotFound());
    }

    @Test
    void getMermaidReturnsGraphSource() throws Exception {
        mockMvc.perform(get("/tracegraph/ui/graph"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    assertThat(body).containsAnyOf("validate", "graph", "flowchart");
                });
    }

    @Test
    void getComplexityReturnsMetrics() throws Exception {
        mockMvc.perform(get("/tracegraph/ui/complexity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodeCount").value(5));
    }

    @Test
    void paginationLimitWorks() throws Exception {
        mockMvc.perform(get("/tracegraph/traces?limit=2&offset=0"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String[] ids = objectMapper.readValue(
                            result.getResponse().getContentAsString(), String[].class);
                    assertThat(ids).hasSize(2);
                });
    }

    @Test
    void paginationEmptyPage() throws Exception {
        mockMvc.perform(get("/tracegraph/traces?limit=2&offset=9999"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String[] ids = objectMapper.readValue(
                            result.getResponse().getContentAsString(), String[].class);
                    assertThat(ids).isEmpty();
                });
    }

    @Test
    void negativeOffsetReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/tracegraph/traces?limit=2&offset=-1"))
                .andExpect(status().isBadRequest());
    }

    // CORS preflight is verified at the auto-config level in tracegraph-spring-boot-starter.
    // In a real deployment the CorsAutoConfiguration bean adds Access-Control-Allow-Origin
    // for http://localhost:5173 and http://localhost:3000 via WebMvcConfigurer.addCorsMappings.
}
