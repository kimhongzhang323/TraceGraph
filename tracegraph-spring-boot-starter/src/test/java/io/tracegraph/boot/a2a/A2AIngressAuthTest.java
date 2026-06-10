package io.tracegraph.boot.a2a;

import io.tracegraph.boot.web.ApiKeyAuthFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class A2AIngressAuthTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(A2AAutoConfiguration.class));

    @Test
    void registersControllerAndAuthFilterInWebApps() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(A2AController.class);
            assertThat(ctx).hasBean("a2aApiKeyAuthFilter");
        });
    }

    @Test
    void disabledPropertyRemovesIngress() {
        runner.withPropertyValues("tracegraph.a2a.enabled=false").run(ctx ->
                assertThat(ctx).doesNotHaveBean(A2AController.class));
    }

    @Test
    void rejectsAllRequestsWhenNoKeyConfigured() throws Exception {
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter(null, "/a2a");
        MockHttpServletRequest request = post("/a2a/messages");
        request.addHeader("X-Api-Key", "anything");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void rejectsWrongKey() throws Exception {
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter("expected", "/a2a");
        MockHttpServletRequest request = post("/a2a/messages");
        request.addHeader("X-Api-Key", "wrong");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void acceptsCorrectKey() throws Exception {
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter("expected", "/a2a");
        MockHttpServletRequest request = post("/a2a/messages");
        request.addHeader("X-Api-Key", "expected");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void ignoresPathsOutsidePrefix() throws Exception {
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter(null, "/a2a");
        MockHttpServletRequest request = post("/other");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    private static MockHttpServletRequest post(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRequestURI(uri);
        return request;
    }
}
