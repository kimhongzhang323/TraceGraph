package io.tracegraph.boot;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for TraceGraph's Spring Boot integration.
 */
@ConfigurationProperties(prefix = "tracegraph")
public class TraceGraphProperties {

    private final Web web = new Web();
    private final Memory memory = new Memory();
    private final Llm llm = new Llm();
    private final Rag rag = new Rag();

    public Web getWeb() {
        return web;
    }

    public Memory getMemory() {
        return memory;
    }

    public Llm getLlm() {
        return llm;
    }

    public Rag getRag() {
        return rag;
    }

    /**
     * LLM client auto-configuration properties.
     */
    public static class Llm {
        public enum Provider { OPENAI, ANTHROPIC }

        private boolean enabled = true;
        private Provider provider;
        private String apiKey;
        private String endpoint;
        private Duration requestTimeout;
        private String anthropicVersion;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public Provider getProvider() { return provider; }
        public void setProvider(Provider provider) { this.provider = provider; }

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }

        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

        public Duration getRequestTimeout() { return requestTimeout; }
        public void setRequestTimeout(Duration requestTimeout) { this.requestTimeout = requestTimeout; }

        public String getAnthropicVersion() { return anthropicVersion; }
        public void setAnthropicVersion(String anthropicVersion) { this.anthropicVersion = anthropicVersion; }
    }

    /**
     * Web endpoint toggles.
     */
    public static class Web {
        private boolean enabled = true;
        private final Cors cors = new Cors();
        private final RateLimit rateLimit = new RateLimit();
        private final Replay replay = new Replay();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Cors getCors() {
            return cors;
        }

        public RateLimit getRateLimit() {
            return rateLimit;
        }

        public Replay getReplay() {
            return replay;
        }

        public static class Cors {
            private boolean enabled = true;
            private String allowedOrigins = "http://localhost:5173,http://localhost:3000";

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public String getAllowedOrigins() {
                return allowedOrigins;
            }

            public void setAllowedOrigins(String allowedOrigins) {
                this.allowedOrigins = allowedOrigins;
            }
        }

        /** Token-bucket rate limiting for read endpoints. Disabled by default. */
        public static class RateLimit {
            private boolean enabled = false;
            /** Sustained requests per second per remote IP. */
            private int rps = 50;
            /** Maximum burst above the sustained rate. */
            private int burst = 100;

            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }

            public int getRps() { return rps; }
            public void setRps(int rps) { this.rps = rps; }

            public int getBurst() { return burst; }
            public void setBurst(int burst) { this.burst = burst; }
        }

        /** Concurrency controls for trace replay. */
        public static class Replay {
            /** Maximum number of concurrent replay executions. 0 means unlimited. */
            private int maxConcurrent = 10;

            public int getMaxConcurrent() { return maxConcurrent; }
            public void setMaxConcurrent(int maxConcurrent) { this.maxConcurrent = maxConcurrent; }
        }
    }

    /**
     * Memory store configuration.
     */
    public static class Memory {
        private final Jdbc jdbc = new Jdbc();

        public Jdbc getJdbc() {
            return jdbc;
        }

        /**
         * JDBC-backed memory store options.
         */
        public static class Jdbc {
            private boolean enabled = true;
            private boolean initSchema = true;
            private String table;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public boolean isInitSchema() {
                return initSchema;
            }

            public void setInitSchema(boolean initSchema) {
                this.initSchema = initSchema;
            }

            public String getTable() {
                return table;
            }

            public void setTable(String table) {
                this.table = table;
            }
        }
    }

    /**
     * RAG auto-configuration properties.
     */
    public static class Rag {
        private final Embedding embedding = new Embedding();

        public Embedding getEmbedding() {
            return embedding;
        }

        /**
         * Embedding client selection properties.
         */
        public static class Embedding {
            public enum Provider { OPENAI, OLLAMA, GEMINI, COHERE }

            private Provider provider;
            private String apiKey;
            private String model;
            private String baseUrl;

            public Provider getProvider() { return provider; }
            public void setProvider(Provider provider) { this.provider = provider; }

            public String getApiKey() { return apiKey; }
            public void setApiKey(String apiKey) { this.apiKey = apiKey; }

            public String getModel() { return model; }
            public void setModel(String model) { this.model = model; }

            public String getBaseUrl() { return baseUrl; }
            public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        }
    }
}
