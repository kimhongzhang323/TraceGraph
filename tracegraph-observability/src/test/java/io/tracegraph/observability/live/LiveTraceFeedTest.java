package io.tracegraph.observability.live;

import io.tracegraph.core.Status;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class LiveTraceFeedTest {

    private static final class Collecting implements Flow.Subscriber<LiveTraceEvent> {
        final List<LiveTraceEvent> events = Collections.synchronizedList(new ArrayList<>());
        final CountDownLatch completed = new CountDownLatch(1);

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(LiveTraceEvent item) {
            events.add(item);
        }

        @Override
        public void onError(Throwable throwable) {
            completed.countDown();
        }

        @Override
        public void onComplete() {
            completed.countDown();
        }
    }

    @Test
    void broadcastsLifecycleEventsInOrder() {
        LiveTraceFeed feed = new LiveTraceFeed();
        Collecting subscriber = new Collecting();
        feed.subscribe(subscriber);

        feed.recordStart("e1", "init");
        feed.recordEnter("e1", "validate", 1, "init");
        feed.recordRetry("e1", "validate", 2, new RuntimeException("flaky"));
        feed.recordExit("e1", "validate", 2, "init", "validated", 5_000_000L);
        feed.recordComplete("e1", Status.COMPLETED, "validated");

        assertThat(subscriber.events).extracting(LiveTraceEvent::type).containsExactly(
                LiveTraceEvent.Type.START, LiveTraceEvent.Type.ENTER, LiveTraceEvent.Type.RETRY,
                LiveTraceEvent.Type.EXIT, LiveTraceEvent.Type.COMPLETE);
        assertThat(subscriber.events.get(2).detail()).isEqualTo("flaky");
        assertThat(subscriber.events.get(3).detail()).isEqualTo("5ms");
        assertThat(subscriber.events.get(4).detail()).isEqualTo("COMPLETED");
    }

    @Test
    void executionFilterDeliversOnlyMatchingEvents() {
        LiveTraceFeed feed = new LiveTraceFeed();
        Collecting filtered = new Collecting();
        Collecting all = new Collecting();
        feed.subscribe(filtered, "wanted");
        feed.subscribe(all);

        feed.recordStart("wanted", "a");
        feed.recordStart("other", "b");

        assertThat(filtered.events).extracting(LiveTraceEvent::executionId).containsExactly("wanted");
        assertThat(all.events).extracting(LiveTraceEvent::executionId).containsExactly("wanted", "other");
    }

    @Test
    void errorEventsCarryTheMessage() {
        LiveTraceFeed feed = new LiveTraceFeed();
        Collecting subscriber = new Collecting();
        feed.subscribe(subscriber);

        feed.recordError("e1", "charge", new IllegalStateException("card declined"));

        assertThat(subscriber.events).hasSize(1);
        assertThat(subscriber.events.get(0).type()).isEqualTo(LiveTraceEvent.Type.ERROR);
        assertThat(subscriber.events.get(0).detail()).isEqualTo("card declined");
    }

    @Test
    void closeCompletesSubscribersAndClearsThem() throws Exception {
        LiveTraceFeed feed = new LiveTraceFeed();
        Collecting subscriber = new Collecting();
        feed.subscribe(subscriber);
        assertThat(feed.subscriberCount()).isEqualTo(1);

        feed.close();

        assertThat(subscriber.completed.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(feed.subscriberCount()).isZero();
    }

    @Test
    void publishingWithNoSubscribersIsANoop() {
        LiveTraceFeed feed = new LiveTraceFeed();
        feed.recordStart("e1", "init");
        feed.recordComplete("e1", Status.COMPLETED, "done");
        assertThat(feed.subscriberCount()).isZero();
    }
}
