package io.tracegraph.observability.replay;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TraceStepTest {

    @Test
    void leafFactoryProducesEmptyChildren() {
        TraceStep<String> s = TraceStep.leaf(0, "node", 1, "before", "after", Duration.ofMillis(10), null);
        assertThat(s.children()).isEmpty();
    }

    @Test
    void nullChildrenNormalizedToEmpty() {
        TraceStep<String> s = new TraceStep<>(0, "node", 1, "before", "after", Duration.ofMillis(10), null, null);
        assertThat(s.children()).isEmpty();
    }

    @Test
    void childrenPreservedOnConstruction() {
        TraceStep<String> child = TraceStep.leaf(0, "child", 1, "b", "a", Duration.ofMillis(5), null);
        TraceStep<String> parent = new TraceStep<>(0, "parent", 1, "b", "a", Duration.ofMillis(20), null, List.of(child));
        assertThat(parent.children()).hasSize(1);
        assertThat(parent.children().get(0).nodeName()).isEqualTo("child");
    }

    @Test
    void childrenListIsImmutable() {
        TraceStep<String> s = TraceStep.leaf(0, "node", 1, "b", "a", Duration.ofMillis(1), null);
        assertThat(s.children()).isUnmodifiable();
    }
}
