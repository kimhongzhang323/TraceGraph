package io.tracegraph.core;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecordContractTest {

    @Test
    void edge_equalsHashCode() {
        // Edge has a Predicate lambda field — verify equality by from/to only via manual checks
        Edge<String> e1 = new Edge<>("a", "b", null);
        Edge<String> e2 = new Edge<>("a", "b", null);
        Edge<String> e3 = new Edge<>("a", "c", null);
        assertThat(e1).isEqualTo(e2);
        assertThat(e1.hashCode()).isEqualTo(e2.hashCode());
        assertThat(e1).isNotEqualTo(e3);
    }

    @Test
    void send_equalsHashCode() {
        Send<String> s1 = Send.to("node", "payload");
        Send<String> s2 = Send.to("node", "payload");
        Send<String> s3 = Send.to("node", "other");
        assertThat(s1).isEqualTo(s2);
        assertThat(s1.hashCode()).isEqualTo(s2.hashCode());
        assertThat(s1).isNotEqualTo(s3);
    }

    @Test
    void executionResult_equalsHashCode() {
        ExecutionResult<String> r1 = new ExecutionResult<>("id1", "state", java.util.List.of("a"), Status.COMPLETED, null);
        ExecutionResult<String> r2 = new ExecutionResult<>("id1", "state", java.util.List.of("a"), Status.COMPLETED, null);
        ExecutionResult<String> r3 = new ExecutionResult<>("id2", "state", java.util.List.of("a"), Status.COMPLETED, null);
        assertThat(r1).isEqualTo(r2);
        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
        assertThat(r1).isNotEqualTo(r3);
    }

    @Test
    void checkpoint_equalsHashCode() {
        Checkpoint<String> c1 = new Checkpoint<>("exec1", "node", "state", Instant.EPOCH, false);
        Checkpoint<String> c2 = new Checkpoint<>("exec1", "node", "state", Instant.EPOCH, false);
        Checkpoint<String> c3 = new Checkpoint<>("exec2", "node", "state", Instant.EPOCH, false);
        assertThat(c1).isEqualTo(c2);
        assertThat(c1.hashCode()).isEqualTo(c2.hashCode());
        assertThat(c1).isNotEqualTo(c3);
    }

    @Test
    void retryPolicy_equalsContract() {
        // RetryPolicy has BackoffStrategy + Predicate lambdas: verify using the static factories
        RetryPolicy p1 = RetryPolicy.none();
        RetryPolicy p2 = RetryPolicy.none();
        assertThat(p1).isEqualTo(p2);
        assertThat(p1.hashCode()).isEqualTo(p2.hashCode());
        assertThat(p1).isNotEqualTo(null);
    }
}
