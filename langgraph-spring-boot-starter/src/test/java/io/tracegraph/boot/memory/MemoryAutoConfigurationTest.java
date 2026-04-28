package io.tracegraph.boot.memory;

import io.tracegraph.boot.TraceGraphAutoConfiguration;
import io.tracegraph.core.spi.MemoryStore;
import io.tracegraph.memory.JdbcMemoryStore;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    TraceGraphAutoConfiguration.class, MemoryAutoConfiguration.class));

    @Test
    void registersJdbcMemoryStoreWhenDataSourcePresent() {
        runner.withBean(DataSource.class, MemoryAutoConfigurationTest::h2DataSource).run(ctx -> {
            assertThat(ctx).hasSingleBean(MemoryStore.class);
            assertThat(ctx.getBean(MemoryStore.class)).isInstanceOf(JdbcMemoryStore.class);

            MemoryStore store = ctx.getBean(MemoryStore.class);
            store.put("s", "k", "v");
            assertThat(store.get("s", "k")).contains("v");
        });
    }

    @Test
    void fallsBackToNoopWhenNoDataSource() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(MemoryStore.class);
            assertThat(ctx.getBean(MemoryStore.class)).isNotInstanceOf(JdbcMemoryStore.class);
        });
    }

    @Test
    void respectsDisablingProperty() {
        runner.withBean(DataSource.class, MemoryAutoConfigurationTest::h2DataSource)
                .withPropertyValues("tracegraph.memory.jdbc.enabled=false")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(MemoryStore.class);
                    assertThat(ctx.getBean(MemoryStore.class)).isNotInstanceOf(JdbcMemoryStore.class);
                });
    }

    @Test
    void userMemoryStoreOverridesAutoConfigured() {
        MemoryStore custom = MemoryStore.noop();
        runner.withBean(DataSource.class, MemoryAutoConfigurationTest::h2DataSource)
                .withBean("customMemoryStore", MemoryStore.class, () -> custom)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(MemoryStore.class);
                    assertThat(ctx.getBean(MemoryStore.class)).isSameAs(custom);
                });
    }

    @Test
    void honoursCustomTableName() {
        runner.withBean(DataSource.class, MemoryAutoConfigurationTest::h2DataSource)
                .withPropertyValues("tracegraph.memory.jdbc.table=my_memory")
                .run(ctx -> {
                    MemoryStore store = ctx.getBean(MemoryStore.class);
                    assertThat(store).isInstanceOf(JdbcMemoryStore.class);
                    store.put("s", "k", 42);
                    assertThat(store.get("s", "k")).contains(42);
                });
    }

    private static DataSource h2DataSource() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        return ds;
    }
}
