package io.tracegraph.boot.rag;

import io.tracegraph.boot.TraceGraphProperties;
import io.tracegraph.core.spi.VectorStore;
import io.tracegraph.rag.InMemoryVectorStore;
import io.tracegraph.rag.PgVectorStore;
import io.tracegraph.rag.PineconeVectorStore;
import io.tracegraph.rag.QdrantVectorStore;
import io.tracegraph.rag.WeaviateVectorStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

@AutoConfiguration
@ConditionalOnClass({VectorStore.class, InMemoryVectorStore.class})
@EnableConfigurationProperties(TraceGraphProperties.class)
public class VectorStoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(VectorStore.class)
    @ConditionalOnProperty(prefix = "tracegraph.rag.vectorstore", name = "provider",
            havingValue = "qdrant")
    public VectorStore qdrantVectorStore(TraceGraphProperties properties) {
        TraceGraphProperties.Rag.VectorStoreConfig cfg = properties.getRag().getVectorstore();
        QdrantVectorStore.Builder b = QdrantVectorStore.builder();
        if (cfg.getBaseUrl() != null) b.baseUrl(cfg.getBaseUrl());
        if (cfg.getApiKey() != null) b.apiKey(cfg.getApiKey());
        return b.build();
    }

    @Bean
    @ConditionalOnMissingBean(VectorStore.class)
    @ConditionalOnProperty(prefix = "tracegraph.rag.vectorstore", name = "provider",
            havingValue = "weaviate")
    public VectorStore weaviateVectorStore(TraceGraphProperties properties) {
        TraceGraphProperties.Rag.VectorStoreConfig cfg = properties.getRag().getVectorstore();
        WeaviateVectorStore.Builder b = WeaviateVectorStore.builder();
        if (cfg.getBaseUrl() != null) b.baseUrl(cfg.getBaseUrl());
        if (cfg.getApiKey() != null) b.apiKey(cfg.getApiKey());
        return b.build();
    }

    @Bean
    @ConditionalOnMissingBean(VectorStore.class)
    @ConditionalOnProperty(prefix = "tracegraph.rag.vectorstore", name = "provider",
            havingValue = "pinecone")
    public VectorStore pineconeVectorStore(TraceGraphProperties properties) {
        TraceGraphProperties.Rag.VectorStoreConfig cfg = properties.getRag().getVectorstore();
        PineconeVectorStore.Builder b = PineconeVectorStore.builder();
        if (cfg.getIndexHost() != null) b.indexHost(cfg.getIndexHost());
        if (cfg.getApiKey() != null) b.apiKey(cfg.getApiKey());
        return b.build();
    }

    @Bean
    @ConditionalOnMissingBean(VectorStore.class)
    @ConditionalOnClass(DataSource.class)
    @ConditionalOnProperty(prefix = "tracegraph.rag.vectorstore", name = "provider",
            havingValue = "pgvector")
    public VectorStore pgVectorStore(DataSource dataSource) {
        return PgVectorStore.of(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean(VectorStore.class)
    @ConditionalOnProperty(prefix = "tracegraph.rag.vectorstore", name = "provider",
            havingValue = "inmemory", matchIfMissing = true)
    public VectorStore inMemoryVectorStore() {
        return new InMemoryVectorStore();
    }
}
