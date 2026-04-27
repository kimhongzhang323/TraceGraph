package io.tracegraph.observability.replay;

import io.tracegraph.core.Status;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReplayerTest {

    @Test
    void exposesStepsAndStateAccessors() {
        ExecutionTrace<String> trace = new ExecutionTrace<>("e1", "seed", "seed.a.b",
                Status.COMPLETED, null,
                List.of(
                        new TraceStep<>(0, "a", 1, "seed", "seed.a", Duration.ZERO, null),
                        new TraceStep<>(1, "b", 1, "seed.a", "seed.a.b", Duration.ZERO, null)),
                Instant.EPOCH, Instant.EPOCH);

        Replayer<String> r = Replayer.of(trace);

        assertThat(r.executionId()).isEqualTo("e1");
        assertThat(r.status()).isEqualTo(Status.COMPLETED);
        assertThat(r.stepCount()).isEqualTo(2);
        assertThat(r.pathTaken()).containsExactly("a", "b");
        assertThat(r.stateAt(-1)).isEqualTo("seed");
        assertThat(r.stateAt(0)).isEqualTo("seed.a");
        assertThat(r.stateAt(1)).isEqualTo("seed.a.b");
        assertThat(r.stepAt(0).nodeName()).isEqualTo("a");
    }

    @Test
    void stateAtRejectsOutOfRangeIndex() {
        ExecutionTrace<String> trace = new ExecutionTrace<>("e1", "x", "x",
                Status.COMPLETED, null, List.of(), Instant.EPOCH, Instant.EPOCH);

        Replayer<String> r = Replayer.of(trace);
        assertThatThrownBy(() -> r.stateAt(0)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> r.stateAt(-2)).isInstanceOf(IndexOutOfBoundsException.class);
    }
}
