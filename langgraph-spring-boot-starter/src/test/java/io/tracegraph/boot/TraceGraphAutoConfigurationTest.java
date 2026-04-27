package io.tracegraph.boot;

import io.tracegraph.core.ExecutionResult;
import io.tracegraph.core.Graph;
import io.tracegraph.core.spi.CheckpointStore;
import io.tracegraph.core.spi.MemoryStore;
import io.tracegraph.core.spi.NodeListener;
import io.tracegraph.core.spi.TraceRecorder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class TraceGraphAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TraceGraphAutoConfiguration.class));

    @Test
    void registersNoopSpiBeansByDefault() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(NodeListener.class);
            assertThat(ctx).hasSingleBean(CheckpointStore.class);
            assertThat(ctx).hasSingleBean(TraceRecorder.class);
            assertThat(ctx).hasSingleBean(MemoryStore.class);
            assertThat(ctx.getBean(NodeListener.class)).isSameAs(NodeListener.NOOP);
        });
    }

    @Test
    void userListenerOverridesAutoConfigured() {
        NodeListener custom = new NodeListener() {};
        runner.withBean(NodeListener.class, () -> custom).run(ctx -> {
            assertThat(ctx).hasSingleBean(NodeListener.class);
            assertThat(ctx.getBean(NodeListener.class)).isSameAs(custom);
        });
    }

    @Test
    void userMemoryStoreOverridesAutoConfigured() {
        MemoryStore custom = MemoryStore.noop();
        runner.withBean("customMemoryStore", MemoryStore.class, () -> custom).run(ctx -> {
            assertThat(ctx).hasSingleBean(MemoryStore.class);
            assertThat(ctx.getBean(MemoryStore.class)).isSameAs(custom);
        });
    }

    @Test
    void userGraphRunsWithAutoWiredSpis() {
        runner.withUserConfiguration(GraphConfig.class).run(ctx -> {
            @SuppressWarnings("unchecked")
            Graph<String> graph = ctx.getBean(Graph.class);
            ExecutionResult<String> result = graph.run("seed");
            assertThat(result.finalState()).isEqualTo("seed.go");
        });
    }

    @Configuration
    static class GraphConfig {
        @Bean
        Graph<String> stringGraph(NodeListener listener,
                                  CheckpointStore checkpoints,
                                  TraceRecorder recorder,
                                  MemoryStore memory) {
            return Graph.<String>builder()
                    .node("go", (s, ctx) -> s + ".go")
                    .entry("go").terminal("go")
                    .listener(listener)
                    .checkpointStore(checkpoints)
                    .traceRecorder(recorder)
                    .memoryStore(memory)
                    .build();
        }
    }
}
