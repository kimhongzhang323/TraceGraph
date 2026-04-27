package io.tracegraph.boot.web;

import io.tracegraph.boot.TraceGraphAutoConfiguration;
import io.tracegraph.observability.replay.TraceStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.DispatcherServlet;

@AutoConfiguration(after = TraceGraphAutoConfiguration.class)
@ConditionalOnClass({DispatcherServlet.class, TraceStore.class})
@ConditionalOnWebApplication
@ConditionalOnBean(TraceStore.class)
public class TraceWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TraceController traceController(TraceStore store) {
        return new TraceController(store);
    }
}
