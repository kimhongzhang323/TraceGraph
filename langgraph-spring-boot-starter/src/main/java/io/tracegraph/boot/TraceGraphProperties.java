package io.tracegraph.boot;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tracegraph")
public class TraceGraphProperties {

    private final Web web = new Web();
    private final Memory memory = new Memory();
    private final Llm llm = new Llm();

    public Web getWeb() {
        return web;
    }

    public Memory getMemory() {
        return memory;
    }

    public Llm getLlm() {
        return llm;
    }

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

    public static class Web {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Memory {
        private final Jdbc jdbc = new Jdbc();

        public Jdbc getJdbc() {
            return jdbc;
        }

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
}
