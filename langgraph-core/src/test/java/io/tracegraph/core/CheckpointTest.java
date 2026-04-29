package io.tracegraph.core;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckpointTest {

    @Test
    void rejectsNullExecutionId() {
        assertThatThrownBy(() -> new Checkpoint<>(null, "n", "s", Instant.now(), false))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullNode() {
        assertThatThrownBy(() -> new Checkpoint<>("e", null, "s", Instant.now(), false))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullTimestamp() {
        assertThatThrownBy(() -> new Checkpoint<>("e", "n", "s", null, false))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void allowsNullState() {
        Checkpoint<String> cp = new Checkpoint<>("e", "n", null, Instant.EPOCH, false);
        assertThat(cp.state()).isNull();
    }
}
