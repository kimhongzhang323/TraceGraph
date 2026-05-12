package io.tracegraph.boot.llm;

import io.tracegraph.boot.TraceGraphProperties;
import io.tracegraph.connectors.llm.AnthropicLlmClient;
import io.tracegraph.connectors.llm.DeepSeekLlmClient;
import io.tracegraph.connectors.llm.GeminiLlmClient;
import io.tracegraph.connectors.llm.LlmClient;
import io.tracegraph.connectors.llm.OpenAiLlmClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.net.URI;

@AutoConfiguration
@ConditionalOnClass(LlmClient.class)
@ConditionalOnProperty(prefix = "tracegraph.llm", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(TraceGraphProperties.class)
public class LlmAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(LlmClient.class)
    @ConditionalOnProperty(prefix = "tracegraph.llm", name = "provider", havingValue = "openai")
    public LlmClient openAiLlmClient(TraceGraphProperties properties) {
        TraceGraphProperties.Llm llm = properties.getLlm();
        OpenAiLlmClient.Builder b = OpenAiLlmClient.builder();
        if (llm.getEndpoint() != null) b.endpoint(URI.create(llm.getEndpoint()));
        if (llm.getApiKey() != null) b.apiKey(llm.getApiKey());
        if (llm.getRequestTimeout() != null) b.requestTimeout(llm.getRequestTimeout());
        return b.build();
    }

    @Bean
    @ConditionalOnMissingBean(LlmClient.class)
    @ConditionalOnProperty(prefix = "tracegraph.llm", name = "provider", havingValue = "anthropic")
    public LlmClient anthropicLlmClient(TraceGraphProperties properties) {
        TraceGraphProperties.Llm llm = properties.getLlm();
        AnthropicLlmClient.Builder b = AnthropicLlmClient.builder();
        if (llm.getEndpoint() != null) b.endpoint(URI.create(llm.getEndpoint()));
        if (llm.getApiKey() != null) b.apiKey(llm.getApiKey());
        if (llm.getRequestTimeout() != null) b.requestTimeout(llm.getRequestTimeout());
        if (llm.getAnthropicVersion() != null) b.anthropicVersion(llm.getAnthropicVersion());
        return b.build();
    }

    @Bean
    @ConditionalOnMissingBean(LlmClient.class)
    @ConditionalOnProperty(prefix = "tracegraph.llm", name = "provider", havingValue = "gemini")
    public LlmClient geminiLlmClient(TraceGraphProperties properties) {
        TraceGraphProperties.Llm llm = properties.getLlm();
        GeminiLlmClient.Builder b = GeminiLlmClient.builder();
        if (llm.getApiKey() != null) b.apiKey(llm.getApiKey());
        if (llm.getRequestTimeout() != null) b.requestTimeout(llm.getRequestTimeout());
        return b.build();
    }

    @Bean
    @ConditionalOnMissingBean(LlmClient.class)
    @ConditionalOnProperty(prefix = "tracegraph.llm", name = "provider", havingValue = "deepseek")
    public LlmClient deepSeekLlmClient(TraceGraphProperties properties) {
        TraceGraphProperties.Llm llm = properties.getLlm();
        DeepSeekLlmClient.Builder b = DeepSeekLlmClient.builder();
        if (llm.getApiKey() != null) b.apiKey(llm.getApiKey());
        if (llm.getEndpoint() != null) b.endpoint(java.net.URI.create(llm.getEndpoint()));
        if (llm.getRequestTimeout() != null) b.requestTimeout(llm.getRequestTimeout());
        return b.build();
    }
}
