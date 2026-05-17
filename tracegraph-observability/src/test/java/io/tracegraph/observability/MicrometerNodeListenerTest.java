package io.tracegraph.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MicrometerNodeListenerTest {

    @Test
    void recordsNodeDurationOnExit() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerNodeListener listener = new MicrometerNodeListener(registry);

        listener.onEnter("classify", "state-in");
        sleepQuietly(10);
        listener.onExit("classify", "state-out");

        Timer timer = registry.find("tracegraph.node.duration").tag("node", "classify").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isGreaterThanOrEqualTo(10.0);
    }

    @Test
    void recordsErrorCounterAndDurationOnError() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerNodeListener listener = new MicrometerNodeListener(registry);

        listener.onEnter("flaky", "state-in");
        listener.onError("flaky", new RuntimeException("boom"));

        Counter errors = registry.find("tracegraph.node.errors")
                .tag("node", "flaky")
                .tag("exception", "RuntimeException")
                .counter();
        assertThat(errors).isNotNull();
        assertThat(errors.count()).isEqualTo(1.0);

        Timer timer = registry.find("tracegraph.node.duration").tag("node", "flaky").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    void emitsTokenCountersOnUsage() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerNodeListener listener = new MicrometerNodeListener(registry);

        listener.onEnter("chat", "in");
        listener.onUsage("chat", 30, 12);
        listener.onExit("chat", "out");

        Counter prompt = registry.find("tracegraph.llm.tokens").tag("node", "chat").tag("type", "prompt").counter();
        Counter completion = registry.find("tracegraph.llm.tokens").tag("node", "chat").tag("type", "completion").counter();

        assertThat(prompt).isNotNull();
        assertThat(prompt.count()).isEqualTo(30.0);
        assertThat(completion).isNotNull();
        assertThat(completion.count()).isEqualTo(12.0);
    }

    @Test
    void multipleNodesGetSeparateTimers() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerNodeListener listener = new MicrometerNodeListener(registry);

        listener.onEnter("a", null);
        listener.onExit("a", null);
        listener.onEnter("b", null);
        listener.onExit("b", null);
        listener.onEnter("a", null);
        listener.onExit("a", null);

        Timer aTimer = registry.find("tracegraph.node.duration").tag("node", "a").timer();
        Timer bTimer = registry.find("tracegraph.node.duration").tag("node", "b").timer();

        assertThat(aTimer.count()).isEqualTo(2);
        assertThat(bTimer.count()).isEqualTo(1);
    }

    @Test
    void exitWithoutMatchingEnterIsIgnored() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerNodeListener listener = new MicrometerNodeListener(registry);

        listener.onExit("orphan", null);

        Timer timer = registry.find("tracegraph.node.duration").tag("node", "orphan").timer();
        assertThat(timer).isNull();
    }

    @Test
    void rejectsNullRegistry() {
        assertThatThrownBy(() -> new MicrometerNodeListener(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void customPrefixAppliedToAllMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerNodeListener listener = MicrometerNodeListener.builder(registry)
                .prefix("app")
                .build();

        listener.onEnter("classify", null);
        listener.onUsage("classify", 5, 7);
        listener.onExit("classify", null);

        assertThat(registry.find("app.node.duration").tag("node", "classify").timer()).isNotNull();
        assertThat(registry.find("app.llm.tokens").tag("node", "classify").tag("type", "prompt").counter()).isNotNull();

        listener.onEnter("fail", null);
        listener.onError("fail", new IllegalStateException("x"));
        assertThat(registry.find("app.node.errors").tag("node", "fail").counter()).isNotNull();
    }

    @Test
    void nestedNodesPopInLifoOrder() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerNodeListener listener = new MicrometerNodeListener(registry);

        listener.onEnter("outer", null);
        listener.onEnter("inner", null);
        listener.onExit("inner", null);
        listener.onExit("outer", null);

        assertThat(registry.find("tracegraph.node.duration").tag("node", "outer").timer().count()).isEqualTo(1);
        assertThat(registry.find("tracegraph.node.duration").tag("node", "inner").timer().count()).isEqualTo(1);
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
