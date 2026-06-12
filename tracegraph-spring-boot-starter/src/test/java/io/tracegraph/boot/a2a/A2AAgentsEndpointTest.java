package io.tracegraph.boot.a2a;

import io.tracegraph.a2a.AgentCard;
import io.tracegraph.a2a.InMemoryAgentBus;
import io.tracegraph.boot.web.ApiKeyAuthFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class A2AAgentsEndpointTest {

    @Test
    void listsRegisteredAgentCards() throws Exception {
        InMemoryAgentBus bus = new InMemoryAgentBus();
        bus.registerCard(new AgentCard("orders", "Orders", "order lookup and refunds",
                "https://agents.example/a2a", List.of("order-lookup", "refunds")));
        bus.registerCard(AgentCard.of("billing"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new A2AController(bus)).build();

        mvc.perform(get("/a2a/agents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("billing"))
                .andExpect(jsonPath("$[1].id").value("orders"))
                .andExpect(jsonPath("$[1].skills[0]").value("order-lookup"));
    }

    @Test
    void emptyBusReturnsEmptyList() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new A2AController(new InMemoryAgentBus())).build();

        mvc.perform(get("/a2a/agents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void discoveryEndpointSitsBehindTheA2aApiKeyFilter() throws Exception {
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter("expected", "/a2a");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/a2a/agents");
        request.setRequestURI("/a2a/agents");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }
}
