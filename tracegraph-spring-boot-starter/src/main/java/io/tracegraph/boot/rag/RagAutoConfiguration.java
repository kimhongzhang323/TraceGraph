package io.tracegraph.boot.rag;

import io.tracegraph.core.spi.EmbeddingClient;
import io.tracegraph.core.spi.VectorStore;
import io.tracegraph.rag.Retriever;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass({Retriever.class, VectorStore.class, EmbeddingClient.class})
@ConditionalOnBean({VectorStore.class, EmbeddingClient.class})
@ConditionalOnProperty(prefix = "tracegraph.rag", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RagAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(Retriever.class)
    public Retriever traceGraphRetriever(VectorStore vectorStore, EmbeddingClient embeddingClient) {
        return Retriever.of(vectorStore, embeddingClient);
    }
}
