package io.tracegraph.boot.a2a;

import io.tracegraph.a2a.AgentBus;
import io.tracegraph.a2a.InMemoryAgentBus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class A2AAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(A2AAutoConfiguration.class));

    @Test
    void registersInMemoryAgentBus() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(AgentBus.class);
            assertThat(ctx.getBean(AgentBus.class)).isInstanceOf(InMemoryAgentBus.class);
        });
    }

    @Test
    void userBeanOverridesAutoConfigured() {
        InMemoryAgentBus custom = new InMemoryAgentBus();
        runner.withBean("customBus", AgentBus.class, () -> custom).run(ctx -> {
            assertThat(ctx).hasSingleBean(AgentBus.class);
            assertThat(ctx.getBean(AgentBus.class)).isSameAs(custom);
        });
    }
}
