package io.tracegraph.observability.live;

import io.tracegraph.core.Status;
import io.tracegraph.core.spi.TraceRecorder;

import java.time.Instant;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.function.Predicate;

/**
 * A {@link TraceRecorder} that broadcasts execution events to live subscribers — the backend of
 * the trace-viewer's live execution view ({@code GET /tracegraph/stream}).
 *
 * <p>Wire one instance via {@code Graph.Builder.traceRecorder(...)} (alongside or instead of a
 * persisting recorder) and subscribe with {@link #subscribe(Flow.Subscriber)} or
 * {@link #subscribe(Flow.Subscriber, String)} to follow a single executionId. Each subscriber
 * gets its own buffered publisher; a slow subscriber never blocks the executor — events beyond
 * the buffer are dropped for that subscriber (the durable record lives in the {@code TraceStore},
 * not here).
 *
 * <p>Thread-safe. {@link #close()} completes all current subscribers.
 */
public final class LiveTraceFeed implements TraceRecorder, AutoCloseable {

    private static final int SUBSCRIBER_BUFFER = 256;

    private record Subscription(SubmissionPublisher<LiveTraceEvent> publisher,
                                Predicate<LiveTraceEvent> filter) {}

    private final CopyOnWriteArrayList<Subscription> subscriptions = new CopyOnWriteArrayList<>();

    /** Subscribes to events of every execution. */
    public void subscribe(Flow.Subscriber<LiveTraceEvent> subscriber) {
        subscribe(subscriber, null);
    }

    /** Subscribes to events of {@code executionId} only; {@code null} means all executions. */
    public void subscribe(Flow.Subscriber<LiveTraceEvent> subscriber, String executionId) {
        SubmissionPublisher<LiveTraceEvent> publisher = new SubmissionPublisher<>(
                Runnable::run, SUBSCRIBER_BUFFER);
        Predicate<LiveTraceEvent> filter = executionId == null
                ? e -> true
                : e -> executionId.equals(e.executionId());
        Subscription subscription = new Subscription(publisher, filter);
        subscriptions.add(subscription);
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription s) {
                subscriber.onSubscribe(s);
            }

            @Override
            public void onNext(LiveTraceEvent item) {
                subscriber.onNext(item);
            }

            @Override
            public void onError(Throwable throwable) {
                subscriptions.remove(subscription);
                subscriber.onError(throwable);
            }

            @Override
            public void onComplete() {
                subscriptions.remove(subscription);
                subscriber.onComplete();
            }
        });
    }

    /** Number of live subscribers. */
    public int subscriberCount() {
        return subscriptions.size();
    }

    private void publish(LiveTraceEvent event) {
        for (Subscription subscription : subscriptions) {
            if (subscription.filter().test(event)) {
                subscription.publisher().offer(event, (sub, dropped) -> false);
            }
        }
    }

    @Override
    public void recordStart(String executionId, Object initialState) {
        publish(new LiveTraceEvent(executionId, LiveTraceEvent.Type.START, null, 0, null, Instant.now()));
    }

    @Override
    public void recordEnter(String executionId, String nodeName, int attempt, Object state) {
        publish(new LiveTraceEvent(executionId, LiveTraceEvent.Type.ENTER, nodeName, attempt, null, Instant.now()));
    }

    @Override
    public void recordRetry(String executionId, String nodeName, int attempt, Throwable error) {
        publish(new LiveTraceEvent(executionId, LiveTraceEvent.Type.RETRY, nodeName, attempt,
                error == null ? null : error.getMessage(), Instant.now()));
    }

    @Override
    public void recordExit(String executionId, String nodeName, int attempts,
                           Object before, Object after, long durationNanos) {
        publish(new LiveTraceEvent(executionId, LiveTraceEvent.Type.EXIT, nodeName, attempts,
                durationNanos / 1_000_000 + "ms", Instant.now()));
    }

    @Override
    public void recordError(String executionId, String nodeName, Throwable error) {
        publish(new LiveTraceEvent(executionId, LiveTraceEvent.Type.ERROR, nodeName, 0,
                error == null ? null : error.getMessage(), Instant.now()));
    }

    @Override
    public void recordComplete(String executionId, Status status, Object finalState) {
        publish(new LiveTraceEvent(executionId, LiveTraceEvent.Type.COMPLETE, null, 0,
                status == null ? null : status.name(), Instant.now()));
    }

    @Override
    public void close() {
        for (Subscription subscription : subscriptions) {
            subscription.publisher().close();
        }
        subscriptions.clear();
    }
}
