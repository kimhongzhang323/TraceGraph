package io.tracegraph.observability;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlowNodeListenerTest {

    @Test
    void firesHandlerWhenNamedBudgetExceeded() {
        List<SlowNodeEvent> events = new ArrayList<>();
        SlowNodeListener listener = SlowNodeListener.builder()
                .budget("classify", Duration.ofMillis(5))
                .handler(events::add)
                .build();

        listener.onEnter("classify", null);
        sleepQuietly(20);
        listener.onExit("classify", null);

        assertThat(events).hasSize(1);
        SlowNodeEvent e = events.get(0);
        assertThat(e.nodeName()).isEqualTo("classify");
        assertThat(e.budget()).isEqualTo(Duration.ofMillis(5));
        assertThat(e.duration()).isGreaterThanOrEqualTo(Duration.ofMillis(20));
    }

    @Test
    void doesNotFireHandlerWhenWithinBudget() {
        List<SlowNodeEvent> events = new ArrayList<>();
        SlowNodeListener listener = SlowNodeListener.builder()
                .budget("classify", Duration.ofSeconds(5))
                .handler(events::add)
                .build();

        listener.onEnter("classify", null);
        listener.onExit("classify", null);

        assertThat(events).isEmpty();
    }

    @Test
    void usesDefaultBudgetWhenNodeNotInMap() {
        List<SlowNodeEvent> events = new ArrayList<>();
        SlowNodeListener listener = SlowNodeListener.builder()
                .defaultBudget(Duration.ofMillis(5))
                .handler(events::add)
                .build();

        listener.onEnter("anything", null);
        sleepQuietly(20);
        listener.onExit("anything", null);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).budget()).isEqualTo(Duration.ofMillis(5));
    }

    @Test
    void perNodeBudgetWinsOverDefault() {
        List<SlowNodeEvent> events = new ArrayList<>();
        SlowNodeListener listener = SlowNodeListener.builder()
                .defaultBudget(Duration.ofMillis(1))
                .budget("fast", Duration.ofSeconds(5))
                .handler(events::add)
                .build();

        listener.onEnter("fast", null);
        sleepQuietly(20);
        listener.onExit("fast", null);

        assertThat(events).isEmpty();
    }

    @Test
    void unbudgetedNodeIsIgnoredWhenNoDefault() {
        List<SlowNodeEvent> events = new ArrayList<>();
        SlowNodeListener listener = SlowNodeListener.builder()
                .budget("classify", Duration.ofMillis(5))
                .handler(events::add)
                .build();

        listener.onEnter("untracked", null);
        sleepQuietly(20);
        listener.onExit("untracked", null);

        assertThat(events).isEmpty();
    }

    @Test
    void firesHandlerOnErrorPathToo() {
        List<SlowNodeEvent> events = new ArrayList<>();
        SlowNodeListener listener = SlowNodeListener.builder()
                .budget("flaky", Duration.ofMillis(5))
                .handler(events::add)
                .build();

        listener.onEnter("flaky", null);
        sleepQuietly(20);
        listener.onError("flaky", new RuntimeException("boom"));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).nodeName()).isEqualTo("flaky");
    }

    @Test
    void handlerExceptionDoesNotPropagate() {
        SlowNodeListener listener = SlowNodeListener.builder()
                .budget("classify", Duration.ofMillis(1))
                .handler(e -> {
                    throw new IllegalStateException("handler bug");
                })
                .build();

        listener.onEnter("classify", null);
        sleepQuietly(10);
        // must not throw
        listener.onExit("classify", null);
    }

    @Test
    void doesNotThrowWhenBudgetExceeded() {
        SlowNodeListener listener = SlowNodeListener.builder()
                .budget("classify", Duration.ofMillis(1))
                .handler(e -> {})
                .build();

        listener.onEnter("classify", null);
        sleepQuietly(10);
        // contract: listener notifies, never throws
        listener.onExit("classify", null);
    }

    @Test
    void nestedNodesPopInLifoOrder() {
        List<SlowNodeEvent> events = new ArrayList<>();
        SlowNodeListener listener = SlowNodeListener.builder()
                .defaultBudget(Duration.ofMillis(5))
                .handler(events::add)
                .build();

        listener.onEnter("outer", null);
        sleepQuietly(10);
        listener.onEnter("inner", null);
        listener.onExit("inner", null);
        listener.onExit("outer", null);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).nodeName()).isEqualTo("outer");
    }

    @Test
    void exitWithoutMatchingEnterIsIgnored() {
        List<SlowNodeEvent> events = new ArrayList<>();
        SlowNodeListener listener = SlowNodeListener.builder()
                .defaultBudget(Duration.ofMillis(1))
                .handler(events::add)
                .build();

        listener.onExit("orphan", null);

        assertThat(events).isEmpty();
    }

    @Test
    void mapConstructorAcceptsBudgetsAndHandler() {
        List<SlowNodeEvent> events = new ArrayList<>();
        SlowNodeListener listener = new SlowNodeListener(
                Map.of("classify", Duration.ofMillis(5)),
                events::add);

        listener.onEnter("classify", null);
        sleepQuietly(20);
        listener.onExit("classify", null);

        assertThat(events).hasSize(1);
    }

    @Test
    void rejectsNullHandler() {
        assertThatThrownBy(() -> SlowNodeListener.builder()
                .defaultBudget(Duration.ofMillis(1))
                .build())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsNegativeBudget() {
        assertThatThrownBy(() -> SlowNodeListener.builder()
                .budget("x", Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SlowNodeListener.builder()
                .defaultBudget(Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void perThreadStartTrackingIsIsolated() throws InterruptedException {
        List<SlowNodeEvent> events = new CopyOnWriteArrayList<>();
        SlowNodeListener listener = SlowNodeListener.builder()
                .budget("threadwork", Duration.ofMillis(5))
                .budget("mainwork", Duration.ofSeconds(10))
                .handler(events::add)
                .build();

        Thread t = new Thread(() -> {
            listener.onEnter("threadwork", null);
            sleepQuietly(20);
            listener.onExit("threadwork", null);
        });

        listener.onEnter("mainwork", null);
        t.start();
        t.join();
        listener.onExit("mainwork", null);

        assertThat(events).extracting(SlowNodeEvent::nodeName)
                .containsExactlyInAnyOrder("threadwork");
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
