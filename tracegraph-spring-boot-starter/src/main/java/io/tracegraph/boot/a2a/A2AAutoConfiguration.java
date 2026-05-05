package io.tracegraph.boot.a2a;

import io.tracegraph.a2a.AgentBus;
import io.tracegraph.a2a.InMemoryAgentBus;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration that registers an {@link InMemoryAgentBus} when
 * {@code tracegraph-a2a} is on the classpath and no {@link AgentBus} bean exists.
 */
@AutoConfiguration
@ConditionalOnClass(AgentBus.class)
@ConditionalOnMissingBean(AgentBus.class)
public class A2AAutoConfiguration {

    @Bean
    public InMemoryAgentBus agentBus() {
        return new InMemoryAgentBus();
    }
}
