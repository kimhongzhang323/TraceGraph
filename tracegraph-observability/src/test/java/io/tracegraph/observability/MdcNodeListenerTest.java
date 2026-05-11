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
    void onEnterPutsExecutionIdKeyIntoMdc() {
        MdcNodeListener listener = new MdcNodeListener();
        listener.onEnter("myNode", "state");
        assertThat(MDC.get(MdcNodeListener.DEFAULT_EXECUTION_ID_KEY)).isNotNull();
    }

    @Test
    void onExitRemovesBothKeys() {
        MdcNodeListener listener = new MdcNodeListener();
        listener.onEnter("myNode", "state");
        listener.onExit("myNode", "state");
        assertThat(MDC.get(MdcNodeListener.DEFAULT_NODE_KEY)).isNull();
        assertThat(MDC.get(MdcNodeListener.DEFAULT_EXECUTION_ID_KEY)).isNull();
    }

    @Test
    void onErrorRemovesBothKeys() {
        MdcNodeListener listener = new MdcNodeListener();
        listener.onEnter("myNode", "state");
        listener.onError("myNode", new RuntimeException("boom"));
        assertThat(MDC.get(MdcNodeListener.DEFAULT_NODE_KEY)).isNull();
        assertThat(MDC.get(MdcNodeListener.DEFAULT_EXECUTION_ID_KEY)).isNull();
    }

    @Test
    void customKeyNamesAreRespected() {
        MdcNodeListener listener = new MdcNodeListener("custom.execId", "custom.node");
        listener.onEnter("nodeA", "state");
        assertThat(MDC.get("custom.node")).isEqualTo("nodeA");
        assertThat(MDC.get("custom.execId")).isNotNull();
        listener.onExit("nodeA", "state");
        assertThat(MDC.get("custom.node")).isNull();
        assertThat(MDC.get("custom.execId")).isNull();
    }

    @Test
    void defaultConstructorUsesDefaultConstants() {
        MdcNodeListener listener = new MdcNodeListener();
        listener.onEnter("n", "s");
        assertThat(MDC.get(MdcNodeListener.DEFAULT_NODE_KEY)).isEqualTo("n");
        assertThat(MDC.get(MdcNodeListener.DEFAULT_EXECUTION_ID_KEY)).isNotNull();
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
