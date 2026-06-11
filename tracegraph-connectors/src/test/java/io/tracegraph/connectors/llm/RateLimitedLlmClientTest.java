package io.tracegraph.connectors.llm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(10)
class RateLimitedLlmClientTest {

    private static LlmRequest request(String text) {
        return LlmRequest.builder()
                .model("test-model")
                .messages(List.of(ChatMessage.user(text)))
                .build();
    }

    @Test
    void callsWithinTheBucketDoNotBlock() {
        RateLimitedLlmClient client = RateLimitedLlmClient.of(
                MockLlmClient.constant("ok"), 2, Duration.ofMinutes(1));

        long start = System.nanoTime();
        client.complete(request("one"));
        client.complete(request("two"));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMs).isLessThan(1_000);
    }

    @Test
    void callsBeyondTheBucketBlockUntilRefill() {
        RateLimitedLlmClient client = RateLimitedLlmClient.of(
                MockLlmClient.constant("ok"), 1, Duration.ofMillis(150));

        long start = System.nanoTime();
        client.complete(request("one"));
        client.complete(request("two"));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMs).isGreaterThanOrEqualTo(100);
    }

    @Test
    void concurrentCallersDoNotExceedTheRate() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        LlmClient counting = new MockLlmClient(r -> {
            calls.incrementAndGet();
            return new LlmResponse("ok", LlmResponse.FinishReason.STOP, LlmResponse.Usage.ZERO);
        });
        RateLimitedLlmClient client = RateLimitedLlmClient.of(counting, 1, Duration.ofMillis(150));

        CountDownLatch started = new CountDownLatch(1);
        List<Thread> workers = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) {
            workers.add(Thread.startVirtualThread(() -> {
                try {
                    started.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                client.complete(request("x"));
            }));
        }
        started.countDown();
        Thread.sleep(80);
        int afterFirstWindow = calls.get();
        for (Thread w : workers) {
            w.join();
        }

        assertThat(afterFirstWindow).isLessThanOrEqualTo(1);
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void interruptionWhileBlockedPropagatesAndRestoresFlag() throws Exception {
        RateLimitedLlmClient client = RateLimitedLlmClient.of(
                MockLlmClient.constant("ok"), 1, Duration.ofMinutes(5));
        client.complete(request("drain the bucket"));

        CountDownLatch failed = new CountDownLatch(1);
        AtomicInteger interrupted = new AtomicInteger();
        Thread blocked = Thread.startVirtualThread(() -> {
            try {
                client.complete(request("blocks"));
            } catch (RuntimeException e) {
                if (Thread.currentThread().isInterrupted()) {
                    interrupted.incrementAndGet();
                }
                failed.countDown();
            }
        });
        Thread.sleep(100);
        blocked.interrupt();

        assertThat(failed.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(interrupted.get()).isEqualTo(1);
    }

    @Test
    void rejectsNonPositiveConfiguration() {
        assertThatThrownBy(() -> RateLimitedLlmClient.of(MockLlmClient.constant("x"), 0, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RateLimitedLlmClient.of(MockLlmClient.constant("x"), 1, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void systemNameDelegates() {
        assertThat(RateLimitedLlmClient.of(MockLlmClient.constant("x"), 1, Duration.ofSeconds(1)).systemName())
                .isEqualTo(MockLlmClient.constant("x").systemName());
    }
}
