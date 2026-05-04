package io.tracegraph.boot;

import io.tracegraph.core.spi.CheckpointStore;
import io.tracegraph.core.spi.MemoryStore;
import io.tracegraph.core.spi.NodeListener;
import io.tracegraph.core.spi.TraceRecorder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(TraceGraphProperties.class)
public class TraceGraphAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public NodeListener traceGraphNodeListener() {
        return NodeListener.NOOP;
    }

    @Bean
    @ConditionalOnMissingBean
    public CheckpointStore traceGraphCheckpointStore() {
        return CheckpointStore.noop();
    }

    @Bean
    @ConditionalOnMissingBean
    public TraceRecorder traceGraphTraceRecorder() {
        return TraceRecorder.noop();
    }

    @Bean
    @ConditionalOnMissingBean
    public MemoryStore traceGraphMemoryStore() {
        return MemoryStore.noop();
    }
}
