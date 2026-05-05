package io.tracegraph.boot.rag;

import io.tracegraph.boot.TraceGraphProperties;
import io.tracegraph.core.spi.EmbeddingClient;
import io.tracegraph.rag.CohereEmbeddingClient;
import io.tracegraph.rag.GeminiEmbeddingClient;
import io.tracegraph.rag.OllamaEmbeddingClient;
import io.tracegraph.rag.OpenAiEmbeddingClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configures an {@link EmbeddingClient} bean based on
 * {@code tracegraph.rag.embedding.provider}.
 *
 * <p>Supported providers: {@code openai} (default), {@code ollama}, {@code gemini}, {@code cohere}.
 * No bean is registered when {@code tracegraph.rag.embedding.provider} is not set.
 */
@AutoConfiguration
@ConditionalOnClass(EmbeddingClient.class)
@EnableConfigurationProperties(TraceGraphProperties.class)
public class EmbeddingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(EmbeddingClient.class)
    @ConditionalOnProperty(prefix = "tracegraph.rag.embedding", name = "provider", havingValue = "openai")
    public EmbeddingClient openAiEmbeddingClient(TraceGraphProperties properties) {
        TraceGraphProperties.Rag.Embedding emb = properties.getRag().getEmbedding();
        OpenAiEmbeddingClient.Builder b = OpenAiEmbeddingClient.builder();
        if (emb.getApiKey() != null) b.apiKey(emb.getApiKey());
        if (emb.getModel() != null) b.model(emb.getModel());
        return b.build();
    }

    @Bean
    @ConditionalOnMissingBean(EmbeddingClient.class)
    @ConditionalOnProperty(prefix = "tracegraph.rag.embedding", name = "provider", havingValue = "ollama")
    public EmbeddingClient ollamaEmbeddingClient(TraceGraphProperties properties) {
        TraceGraphProperties.Rag.Embedding emb = properties.getRag().getEmbedding();
        OllamaEmbeddingClient.Builder b = OllamaEmbeddingClient.builder();
        if (emb.getBaseUrl() != null) b.baseUrl(emb.getBaseUrl());
        if (emb.getModel() != null) b.model(emb.getModel());
        return b.build();
    }

    @Bean
    @ConditionalOnMissingBean(EmbeddingClient.class)
    @ConditionalOnProperty(prefix = "tracegraph.rag.embedding", name = "provider", havingValue = "gemini")
    public EmbeddingClient geminiEmbeddingClient(TraceGraphProperties properties) {
        TraceGraphProperties.Rag.Embedding emb = properties.getRag().getEmbedding();
        GeminiEmbeddingClient.Builder b = GeminiEmbeddingClient.builder()
                .apiKey(emb.getApiKey());
        if (emb.getModel() != null) b.model(emb.getModel());
        return b.build();
    }

    @Bean
    @ConditionalOnMissingBean(EmbeddingClient.class)
    @ConditionalOnProperty(prefix = "tracegraph.rag.embedding", name = "provider", havingValue = "cohere")
    public EmbeddingClient cohereEmbeddingClient(TraceGraphProperties properties) {
        TraceGraphProperties.Rag.Embedding emb = properties.getRag().getEmbedding();
        CohereEmbeddingClient.Builder b = CohereEmbeddingClient.builder()
                .apiKey(emb.getApiKey());
        if (emb.getModel() != null) b.model(emb.getModel());
        return b.build();
    }
}
