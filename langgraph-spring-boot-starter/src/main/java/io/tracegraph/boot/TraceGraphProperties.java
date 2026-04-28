package io.tracegraph.boot;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tracegraph")
public class TraceGraphProperties {

    private final Web web = new Web();
    private final Memory memory = new Memory();

    public Web getWeb() {
        return web;
    }

    public Memory getMemory() {
        return memory;
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
