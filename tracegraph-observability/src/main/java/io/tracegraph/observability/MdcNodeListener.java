package io.tracegraph.observability;

import io.tracegraph.core.spi.NodeListener;
import org.slf4j.MDC;

import java.util.Objects;

public final class MdcNodeListener implements NodeListener {

    public static final String DEFAULT_NODE_KEY = "tracegraph.node";

    private final String nodeKey;

    public MdcNodeListener() {
        this(DEFAULT_NODE_KEY);
    }

    public MdcNodeListener(String nodeKey) {
        this.nodeKey = Objects.requireNonNull(nodeKey, "nodeKey");
    }

    @Override
    public void onEnter(String nodeName, Object state) {
        MDC.put(nodeKey, nodeName);
    }

    @Override
    public void onExit(String nodeName, Object state) {
        MDC.remove(nodeKey);
    }

    @Override
    public void onError(String nodeName, Throwable error) {
        MDC.remove(nodeKey);
    }
}
