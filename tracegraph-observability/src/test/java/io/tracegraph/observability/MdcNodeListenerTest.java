package io.tracegraph.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class MdcNodeListenerTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void onEnterPutsNodeNameIntoMdc() {
        MdcNodeListener listener = new MdcNodeListener();
        listener.onEnter("myNode", "state");
        assertThat(MDC.get(MdcNodeListener.DEFAULT_NODE_KEY)).isEqualTo("myNode");
    }

    @Test
    void onExitRemovesNodeKey() {
        MdcNodeListener listener = new MdcNodeListener();
        listener.onEnter("myNode", "state");
        listener.onExit("myNode", "state");
        assertThat(MDC.get(MdcNodeListener.DEFAULT_NODE_KEY)).isNull();
    }

    @Test
    void onErrorRemovesNodeKey() {
        MdcNodeListener listener = new MdcNodeListener();
        listener.onEnter("myNode", "state");
        listener.onError("myNode", new RuntimeException("boom"));
        assertThat(MDC.get(MdcNodeListener.DEFAULT_NODE_KEY)).isNull();
    }

    @Test
    void customKeyNameIsRespected() {
        MdcNodeListener listener = new MdcNodeListener("custom.node");
        listener.onEnter("nodeA", "state");
        assertThat(MDC.get("custom.node")).isEqualTo("nodeA");
        listener.onExit("nodeA", "state");
        assertThat(MDC.get("custom.node")).isNull();
    }

    @Test
    void defaultConstructorUsesDefaultNodeKey() {
        MdcNodeListener listener = new MdcNodeListener();
        listener.onEnter("n", "s");
        assertThat(MDC.get(MdcNodeListener.DEFAULT_NODE_KEY)).isEqualTo("n");
    }

    @Test
    void removeOnExitWithNoPriorStateDoesNotThrow() {
        MdcNodeListener listener = new MdcNodeListener();
        listener.onExit("neverEntered", "state");
        assertThat(MDC.get(MdcNodeListener.DEFAULT_NODE_KEY)).isNull();
    }

    @Test
    void removeOnErrorWithNoPriorStateDoesNotThrow() {
        MdcNodeListener listener = new MdcNodeListener();
        listener.onError("neverEntered", new RuntimeException());
        assertThat(MDC.get(MdcNodeListener.DEFAULT_NODE_KEY)).isNull();
    }
}
