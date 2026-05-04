package io.tracegraph.boot.memory;

import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import io.tracegraph.boot.TraceGraphAutoConfiguration;
import io.tracegraph.boot.TraceGraphProperties;
import io.tracegraph.core.spi.MemoryStore;
import io.tracegraph.memory.JdbcMemoryStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

@AutoConfiguration(before = TraceGraphAutoConfiguration.class)
@ConditionalOnClass({DataSource.class, JdbcMemoryStore.class, PolymorphicTypeValidator.class})
@ConditionalOnBean(DataSource.class)
@ConditionalOnProperty(prefix = "tracegraph.memory.jdbc", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MemoryAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MemoryStore.class)
    public MemoryStore traceGraphJdbcMemoryStore(DataSource dataSource, TraceGraphProperties properties) {
        TraceGraphProperties.Memory.Jdbc jdbc = properties.getMemory().getJdbc();
        JdbcMemoryStore store = jdbc.getTable() == null
                ? JdbcMemoryStore.of(dataSource)
                : JdbcMemoryStore.of(dataSource, jdbc.getTable());
        if (jdbc.isInitSchema()) {
            store.initSchema();
        }
        return store;
    }
}
