package io.tracegraph.boot.rag;

import io.tracegraph.core.spi.EmbeddingClient;
import io.tracegraph.core.spi.VectorStore;
import io.tracegraph.rag.Retriever;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RagAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RagAutoConfiguration.class));

    @Test
    void ragBeanRegisteredWhenBothSpisPresent() {
        runner
                .withBean(VectorStore.class, RagAutoConfigurationTest::mockVectorStore)
                .withBean(EmbeddingClient.class, RagAutoConfigurationTest::mockEmbeddingClient)
                .run(ctx -> assertThat(ctx).hasSingleBean(Retriever.class));
    }

    @Test
    void ragBeanNotRegisteredWhenDisabledProperty() {
        runner
                .withBean(VectorStore.class, RagAutoConfigurationTest::mockVectorStore)
                .withBean(EmbeddingClient.class, RagAutoConfigurationTest::mockEmbeddingClient)
                .withPropertyValues("tracegraph.rag.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(Retriever.class));
    }

    @Test
    void ragBeanNotRegisteredWhenVectorStoreMissing() {
        runner
                .withBean(EmbeddingClient.class, RagAutoConfigurationTest::mockEmbeddingClient)
                .run(ctx -> assertThat(ctx).doesNotHaveBean(Retriever.class));
    }

    private static VectorStore mockVectorStore() {
        return new VectorStore() {
            @Override
            public void upsert(String scope, String id, float[] embedding, Map<String, String> metadata) {
            }

            @Override
            public List<VectorMatch> query(String scope, float[] embedding, int topK) {
                return List.of();
            }

            @Override
            public void delete(String scope, String id) {
            }
        };
    }

    private static EmbeddingClient mockEmbeddingClient() {
        return texts -> texts.stream().map(t -> new float[0]).toList();
    }
}
