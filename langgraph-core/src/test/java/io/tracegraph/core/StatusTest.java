package io.tracegraph.core;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class StatusTest {
    @Test
    void interruptedValueExists() {
        assertThat(Status.valueOf("INTERRUPTED")).isEqualTo(Status.INTERRUPTED);
    }
}
