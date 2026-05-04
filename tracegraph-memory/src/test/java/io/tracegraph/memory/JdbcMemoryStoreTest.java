package io.tracegraph.memory;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcMemoryStoreTest {

    private DataSource dataSource;

    @BeforeEach
    void setUp() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        this.dataSource = ds;
    }

    @Test
    void roundTripsScalarValues() {
        JdbcMemoryStore store = JdbcMemoryStore.of(dataSource);
        store.initSchema();

        store.put("session-1", "name", "alice");
        store.put("session-1", "count", 42);

        assertThat(store.get("session-1", "name")).contains("alice");
        assertThat(store.get("session-1", "count")).contains(42);
    }

    @Test
    void roundTripsCollectionsAndMaps() {
        JdbcMemoryStore store = JdbcMemoryStore.of(dataSource);
        store.initSchema();

        store.put("s", "list", List.of("a", "b", "c"));
        store.put("s", "map", Map.of("k1", 1, "k2", 2));

        assertThat(store.get("s", "list")).contains(List.of("a", "b", "c"));
        assertThat(store.get("s", "map")).contains(Map.of("k1", 1, "k2", 2));
    }

    @Test
    void getReturnsEmptyForMissingKey() {
        JdbcMemoryStore store = JdbcMemoryStore.of(dataSource);
        store.initSchema();
        assertThat(store.get("s", "missing")).isEmpty();
    }

    @Test
    void putOverwritesExistingValue() {
        JdbcMemoryStore store = JdbcMemoryStore.of(dataSource);
        store.initSchema();
        store.put("s", "k", "v1");
        store.put("s", "k", "v2");
        assertThat(store.get("s", "k")).contains("v2");
    }

    @Test
    void deleteReturnsTrueOnHitFalseOnMiss() {
        JdbcMemoryStore store = JdbcMemoryStore.of(dataSource);
        store.initSchema();
        store.put("s", "k", "v");
        assertThat(store.delete("s", "k")).isTrue();
        assertThat(store.delete("s", "k")).isFalse();
        assertThat(store.get("s", "k")).isEmpty();
    }

    @Test
    void keysReturnsOnlyKeysForGivenScope() {
        JdbcMemoryStore store = JdbcMemoryStore.of(dataSource);
        store.initSchema();
        store.put("scope-a", "k1", "v");
        store.put("scope-a", "k2", "v");
        store.put("scope-b", "k3", "v");

        assertThat(store.keys("scope-a")).containsExactlyInAnyOrder("k1", "k2");
        assertThat(store.keys("scope-b")).containsExactly("k3");
        assertThat(store.keys("scope-empty")).isEmpty();
    }

    @Test
    void initSchemaIsIdempotent() {
        JdbcMemoryStore store = JdbcMemoryStore.of(dataSource);
        store.initSchema();
        store.initSchema();
        store.put("s", "k", "v");
        assertThat(store.get("s", "k")).contains("v");
    }

    @Test
    void pagedKeysReturnsSortedSliceFromSql() {
        JdbcMemoryStore store = JdbcMemoryStore.of(dataSource);
        store.initSchema();
        store.put("s", "delta", 1);
        store.put("s", "alpha", 1);
        store.put("s", "charlie", 1);
        store.put("s", "bravo", 1);

        assertThat(store.pagedKeys("s", 0, 2)).containsExactly("alpha", "bravo");
        assertThat(store.pagedKeys("s", 2, 2)).containsExactly("charlie", "delta");
        assertThat(store.pagedKeys("s", 0, 100)).containsExactly("alpha", "bravo", "charlie", "delta");
    }

    @Test
    void scopeAndKeyAreIndependentOfTableName() {
        JdbcMemoryStore store = JdbcMemoryStore.of(dataSource, "my_memory");
        store.initSchema();
        store.put("s", "k", "v");
        assertThat(store.get("s", "k")).contains("v");
    }
}
