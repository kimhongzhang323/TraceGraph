package io.tracegraph.observability.replay;

import io.tracegraph.core.ExecutionResult;
import io.tracegraph.core.Graph;
import io.tracegraph.core.Status;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcTraceStoreTest {

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
    void persistsAndReloadsTraceFromGraphRun() {
        JdbcTraceStore<String> store = JdbcTraceStore.of(dataSource, String.class);
        store.initSchema();

        Graph<String> g = Graph.<String>builder()
                .node("a", (s, ctx) -> s + ".a")
                .node("b", (s, ctx) -> s + ".b")
                .entry("a").edge("a", "b").terminal("b")
                .traceRecorder(new RecordingTraceRecorder(store))
                .build();
        ExecutionResult<String> r = g.run("seed");

        Optional<ExecutionTrace<?>> loaded = store.load(r.executionId());
        assertThat(loaded).isPresent();
        ExecutionTrace<?> t = loaded.get();
        assertThat(t.executionId()).isEqualTo(r.executionId());
        assertThat(t.status()).isEqualTo(Status.COMPLETED);
        assertThat(t.finalState()).isEqualTo("seed.a.b");
        assertThat(t.steps()).hasSize(2);
        assertThat(t.steps().get(0).nodeName()).isEqualTo("a");
        assertThat(t.steps().get(1).nodeName()).isEqualTo("b");
    }

    @Test
    void overwritesPriorTraceForSameExecutionId() {
        JdbcTraceStore<String> store = JdbcTraceStore.of(dataSource, String.class);
        store.initSchema();

        ExecutionTrace<String> v1 = trace("exec-1", "s0", "s1", Status.COMPLETED, 1);
        ExecutionTrace<String> v2 = trace("exec-1", "s0", "s2", Status.COMPLETED, 2);
        store.save(v1);
        store.save(v2);

        ExecutionTrace<?> loaded = store.load("exec-1").orElseThrow();
        assertThat(loaded.finalState()).isEqualTo("s2");
        assertThat(loaded.steps()).hasSize(2);
    }

    @Test
    void returnsEmptyForUnknownId() {
        JdbcTraceStore<String> store = JdbcTraceStore.of(dataSource, String.class);
        store.initSchema();
        assertThat(store.load("nope")).isEmpty();
    }

    @Test
    void deleteRemovesRow() {
        JdbcTraceStore<String> store = JdbcTraceStore.of(dataSource, String.class);
        store.initSchema();
        store.save(trace("exec-1", "s0", "s1", Status.COMPLETED, 1));
        store.delete("exec-1");
        assertThat(store.load("exec-1")).isEmpty();
    }

    @Test
    void listIdsReturnsAllStoredExecutionsInInsertOrder() {
        JdbcTraceStore<String> store = JdbcTraceStore.of(dataSource, String.class);
        store.initSchema();
        store.save(trace("a", "s0", "s1", Status.COMPLETED, 1, Instant.parse("2026-04-28T10:00:00Z")));
        store.save(trace("b", "s0", "s1", Status.COMPLETED, 1, Instant.parse("2026-04-28T11:00:00Z")));
        store.save(trace("c", "s0", "s1", Status.COMPLETED, 1, Instant.parse("2026-04-28T12:00:00Z")));

        assertThat(store.listIds()).containsExactly("a", "b", "c");
    }

    @Test
    void initSchemaIsIdempotent() {
        JdbcTraceStore<String> store = JdbcTraceStore.of(dataSource, String.class);
        store.initSchema();
        store.initSchema();
        store.save(trace("exec-1", "s0", "s1", Status.COMPLETED, 1));
        assertThat(store.load("exec-1")).isPresent();
    }

    @Test
    void preservesForkLineageAcrossRoundTrip() {
        JdbcTraceStore<String> store = JdbcTraceStore.of(dataSource, String.class);
        store.initSchema();
        Instant t = Instant.parse("2026-04-28T12:00:00Z");
        ExecutionTrace<String> fork = new ExecutionTrace<>(
                "fork-1", "s0", "s1", Status.COMPLETED, null,
                List.of(TraceStep.leaf(0, "n", 1, "s0", "s1", Duration.ofMillis(5), null)),
                t, t.plusSeconds(1), "parent-99", 2);
        store.save(fork);

        ExecutionTrace<?> loaded = store.load("fork-1").orElseThrow();
        assertThat(loaded.isFork()).isTrue();
        assertThat(loaded.forkedFromExecutionId()).isEqualTo("parent-99");
        assertThat(loaded.forkedFromStepIndex()).isEqualTo(2);
    }

    @Test
    void preservesFailureAcrossRoundTripAsLossyThrowable() {
        JdbcTraceStore<String> store = JdbcTraceStore.of(dataSource, String.class);
        store.initSchema();
        Instant t = Instant.parse("2026-04-28T12:00:00Z");
        ExecutionTrace<String> failed = new ExecutionTrace<>(
                "exec-fail", "s0", null, Status.FAILED,
                new IllegalStateException("boom"),
                List.of(),
                t, t.plusSeconds(1));
        store.save(failed);

        ExecutionTrace<?> loaded = store.load("exec-fail").orElseThrow();
        assertThat(loaded.status()).isEqualTo(Status.FAILED);
        assertThat(loaded.error()).isNotNull();
        assertThat(loaded.error().getMessage()).contains("IllegalStateException").contains("boom");
    }

    private static ExecutionTrace<String> trace(String id, String initial, String finalState,
                                                Status status, int steps) {
        return trace(id, initial, finalState, status, steps, Instant.parse("2026-04-28T10:00:00Z"));
    }

    private static ExecutionTrace<String> trace(String id, String initial, String finalState,
                                                Status status, int steps, Instant startedAt) {
        List<TraceStep<String>> stepList = new java.util.ArrayList<>();
        String prev = initial;
        for (int i = 0; i < steps; i++) {
            String next = (i == steps - 1) ? finalState : prev + "." + i;
            stepList.add(TraceStep.leaf(i, "n" + i, 1, prev, next, Duration.ofMillis(1), null));
            prev = next;
        }
        return new ExecutionTrace<>(id, initial, finalState, status, null,
                stepList, startedAt, startedAt.plusSeconds(1));
    }
}
