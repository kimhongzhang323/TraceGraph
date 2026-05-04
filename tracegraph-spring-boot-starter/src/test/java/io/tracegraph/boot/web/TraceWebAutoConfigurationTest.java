package io.tracegraph.boot.web;

import io.tracegraph.boot.TraceGraphAutoConfiguration;
import io.tracegraph.observability.replay.InMemoryTraceStore;
import io.tracegraph.observability.replay.TraceStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class TraceWebAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    TraceGraphAutoConfiguration.class, TraceWebAutoConfiguration.class))
            .withBean(TraceStore.class, InMemoryTraceStore::new);

    @Test
    void registersControllerByDefault() {
        runner.run(ctx -> assertThat(ctx).hasSingleBean(TraceController.class));
    }

    @Test
    void omitsControllerWhenWebDisabled() {
        runner.withPropertyValues("tracegraph.web.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(TraceController.class));
    }
}
