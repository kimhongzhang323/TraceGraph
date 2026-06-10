package io.tracegraph.boot.a2a;

import io.tracegraph.a2a.AgentBus;
import io.tracegraph.a2a.InMemoryAgentBus;
import io.tracegraph.boot.TraceGraphProperties;
import io.tracegraph.boot.web.ApiKeyAuthFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for agent-to-agent messaging: registers an {@link InMemoryAgentBus}
 * when {@code tracegraph-a2a} is on the classpath, and (in web applications) the
 * {@link A2AController} ingress endpoint guarded by {@code tracegraph.a2a.api-key}.
 *
 * <p>Inbound messages are rejected with 401 until an API key is configured — A2A peers
 * are machines, so there is no unauthenticated mode.
 */
@AutoConfiguration
@ConditionalOnClass(AgentBus.class)
@ConditionalOnProperty(prefix = "tracegraph.a2a", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(TraceGraphProperties.class)
public class A2AAutoConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(A2AAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(AgentBus.class)
    public InMemoryAgentBus agentBus() {
        return new InMemoryAgentBus();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication
    static class A2AWebConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public A2AController a2aController(AgentBus bus) {
            return new A2AController(bus);
        }

        @Bean
        public FilterRegistrationBean<ApiKeyAuthFilter> a2aApiKeyAuthFilter(TraceGraphProperties props) {
            String apiKey = props.getA2a().getApiKey();
            if (apiKey == null || apiKey.isBlank()) {
                apiKey = null;
                LOG.warn("A2A ingress is enabled but tracegraph.a2a.api-key is not set — "
                        + "all inbound POST /a2a/messages requests will be rejected with 401. "
                        + "Set the key to accept peer messages, or tracegraph.a2a.enabled=false "
                        + "to remove the endpoint.");
            }
            ApiKeyAuthFilter filter = new ApiKeyAuthFilter(apiKey, "/a2a");
            FilterRegistrationBean<ApiKeyAuthFilter> reg = new FilterRegistrationBean<>(filter);
            reg.addUrlPatterns("/a2a/*");
            reg.setOrder(1);
            return reg;
        }
    }
}
