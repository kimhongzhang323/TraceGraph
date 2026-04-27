package io.tracegraph.core.exec;

import io.tracegraph.core.spi.TraceRecorder;

public final class NoopTraceRecorderAccess {

    private static final TraceRecorder INSTANCE = new TraceRecorder() {};

    private NoopTraceRecorderAccess() {}

    public static TraceRecorder instance() {
        return INSTANCE;
    }
}
