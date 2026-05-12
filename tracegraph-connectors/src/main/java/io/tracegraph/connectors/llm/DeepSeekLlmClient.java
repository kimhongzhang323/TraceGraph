package io.tracegraph.connectors.llm;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;

public final class DeepSeekLlmClient implements LlmClient {

    static final URI DEFAULT_ENDPOINT =
            URI.create("https://api.deepseek.com/v1/chat/completions");

    private final URI endpoint;
    private final OpenAiLlmClient delegate;

    private DeepSeekLlmClient(Builder b) {
        this.endpoint = b.endpoint;
        this.delegate = OpenAiLlmClient.builder()
                .endpoint(b.endpoint)
                .apiKey(b.apiKey)
                .httpClient(b.httpClient)
                .requestTimeout(b.requestTimeout)
                .build();
    }

    public static Builder builder() { return new Builder(); }

    public URI endpoint() { return endpoint; }

    @Override
    public LlmResponse complete(LlmRequest request) {
        return delegate.complete(request);
    }

    public static final class Builder {
        private URI endpoint = DEFAULT_ENDPOINT;
        private String apiKey;
        private HttpClient httpClient;
        private Duration requestTimeout;

        private Builder() {}

        public Builder endpoint(URI endpoint) {
            this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
            return this;
        }

        public Builder apiKey(String apiKey) { this.apiKey = apiKey; return this; }
        public Builder httpClient(HttpClient httpClient) { this.httpClient = httpClient; return this; }
        public Builder requestTimeout(Duration timeout) { this.requestTimeout = timeout; return this; }

        public DeepSeekLlmClient build() { return new DeepSeekLlmClient(this); }
    }
}
