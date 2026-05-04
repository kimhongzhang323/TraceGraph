package io.tracegraph.core;

import io.tracegraph.core.spi.NodeListener;

import java.util.concurrent.SubmissionPublisher;

final class StreamingNodeListener<S> implements NodeListener {

    private final String executionId;
    private final SubmissionPublisher<NodeEvent<S>> pub;

    StreamingNodeListener(String executionId, SubmissionPublisher<NodeEvent<S>> pub) {
        this.executionId = executionId;
        this.pub = pub;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onEnter(String nodeName, Object state) {
        pub.submit(new NodeEvent.NodeEnter<>(executionId, nodeName, (S) state));
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onState(String nodeName, Object before, Object after) {
        pub.submit(new NodeEvent.NodeExit<>(executionId, nodeName, (S) before, (S) after));
    }

    @Override
    public void onRetry(String nodeName, int attempt, Throwable error) {
        pub.submit(new NodeEvent.NodeRetry<>(executionId, nodeName, attempt, error));
    }

    @Override
    public void onError(String nodeName, Throwable error) {
        pub.submit(new NodeEvent.Failed<>(executionId, nodeName, error));
    }
}
