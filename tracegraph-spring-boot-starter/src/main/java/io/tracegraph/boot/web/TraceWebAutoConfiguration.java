package io.tracegraph.boot.web;

import io.tracegraph.boot.TraceGraphAutoConfiguration;
import io.tracegraph.boot.TraceGraphProperties;
import io.tracegraph.core.Graph;
import io.tracegraph.observability.replay.TraceStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.DispatcherServlet;

@AutoConfiguration(after = TraceGraphAutoConfiguration.class)
@ConditionalOnClass({DispatcherServlet.class, TraceStore.class})
@ConditionalOnWebApplication
@ConditionalOnBean(TraceStore.class)
@ConditionalOnProperty(prefix = "tracegraph.web", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(TraceGraphProperties.class)
public class TraceWebAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "tracegraph.web", name = "api-key")
    public FilterRegistrationBean<ApiKeyAuthFilter> apiKeyAuthFilter(TraceGraphProperties props) {
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter(props.getWeb().getApiKey());
        FilterRegistrationBean<ApiKeyAuthFilter> reg = new FilterRegistrationBean<>(filter);
        reg.addUrlPatterns("/tracegraph/*");
        reg.setOrder(1);
        return reg;
    }

    @Bean
    @ConditionalOnMissingBean
    public TraceController traceController(TraceStore store) {
        return new TraceController(store);
    }

    @Bean
    @ConditionalOnSingleCandidate(Graph.class)
    @ConditionalOnMissingBean
    public TraceReplayController traceReplayController(TraceStore store, Graph<?> graph, TraceGraphProperties props) {
        return new TraceReplayController(store, graph, props);
    }

    @Bean
    @ConditionalOnSingleCandidate(Graph.class)
    @ConditionalOnMissingBean(TraceStreamController.class)
    @SuppressWarnings({"rawtypes", "unchecked"})
    public TraceStreamController traceStreamController(Graph<?> graph) {
        return new TraceStreamController(graph);
    }
}
