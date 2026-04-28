package io.tracegraph.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.tracegraph.core.Checkpoint;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcCheckpointStoreTest {

    private DataSource dataSource;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        this.dataSource = ds;
        this.mapper = new ObjectMapper();
    }

    @Test
    void roundTripsACheckpoint() {
        JdbcCheckpointStore<String> store = JdbcCheckpointStore.of(dataSource, mapper, String.class);
        store.initSchema();

        Instant t = Instant.parse("2026-04-28T10:15:30Z");
        store.save(new Checkpoint<>("exec-1", "validate", "seed.validated", t));

        Optional<Checkpoint<?>> loaded = store.latest("exec-1");
        assertThat(loaded).isPresent();
        Checkpoint<?> cp = loaded.get();
        assertThat(cp.executionId()).isEqualTo("exec-1");
        assertThat(cp.lastCompletedNode()).isEqualTo("validate");
        assertThat(cp.state()).isEqualTo("seed.validated");
        assertThat(cp.timestamp()).isEqualTo(t);
    }

    @Test
    void overwritesPriorCheckpointForSameExecution() {
        JdbcCheckpointStore<String> store = JdbcCheckpointStore.of(dataSource, mapper, String.class);
        store.initSchema();
        Instant t1 = Instant.parse("2026-04-28T10:00:00Z");
        Instant t2 = Instant.parse("2026-04-28T11:00:00Z");

        store.save(new Checkpoint<>("exec-1", "validate", "v1", t1));
        store.save(new Checkpoint<>("exec-1", "charge", "v2", t2));

        Checkpoint<?> cp = store.latest("exec-1").orElseThrow();
        assertThat(cp.lastCompletedNode()).isEqualTo("charge");
        assertThat(cp.state()).isEqualTo("v2");
        assertThat(cp.timestamp()).isEqualTo(t2);
    }

    @Test
    void returnsEmptyForUnknownExecution() {
        JdbcCheckpointStore<String> store = JdbcCheckpointStore.of(dataSource, mapper, String.class);
        store.initSchema();
        assertThat(store.latest("never-existed")).isEmpty();
    }

    @Test
    void deleteRemovesCheckpoint() {
        JdbcCheckpointStore<String> store = JdbcCheckpointStore.of(dataSource, mapper, String.class);
        store.initSchema();
        store.save(new Checkpoint<>("exec-1", "n", "s", Instant.now()));

        store.delete("exec-1");

        assertThat(store.latest("exec-1")).isEmpty();
    }

    @Test
    void deleteOfUnknownIdIsSilent() {
        JdbcCheckpointStore<String> store = JdbcCheckpointStore.of(dataSource, mapper, String.class);
        store.initSchema();
        store.delete("never-existed");
    }

    @Test
    void initSchemaIsIdempotent() {
        JdbcCheckpointStore<String> store = JdbcCheckpointStore.of(dataSource, mapper, String.class);
        store.initSchema();
        store.initSchema();
        store.save(new Checkpoint<>("exec-1", "n", "s", Instant.now()));
        assertThat(store.latest("exec-1")).isPresent();
    }

    @Test
    void surfacesSerializationFailures() {
        JdbcCheckpointStore<Object> store = JdbcCheckpointStore.of(dataSource, mapper, Object.class);
        store.initSchema();
        Object unserializable = new Object() {
            @SuppressWarnings("unused")
            Object self = this;
        };
        assertThatThrownBy(() -> store.save(new Checkpoint<>("exec-1", "n", unserializable, Instant.now())))
                .isInstanceOf(CheckpointPersistenceException.class)
                .hasMessageContaining("serialize");
    }
}
