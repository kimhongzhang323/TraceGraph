package io.tracegraph.core;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class NodeEventTest {
    @Test
    void eventsCarryExecutionIdAndNodeName() {
        NodeEvent<String> e = new NodeEvent.NodeEnter<>("eid", "n", "before");
        assertThat(e.executionId()).isEqualTo("eid");
        assertThat(e.nodeName()).isEqualTo("n");
    }

    @Test
    void exitCarriesBeforeAndAfter() {
        var e = new NodeEvent.NodeExit<>("eid", "n", "b", "a");
        assertThat(e.before()).isEqualTo("b");
        assertThat(e.after()).isEqualTo("a");
    }
}
