package io.tracegraph.runtime.exec;

import io.tracegraph.core.Context;
import io.tracegraph.core.spi.MemoryStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ContextPropagatingExecutorTest {

    private static final Context STUB_CONTEXT = new Context() {
        @Override public String executionId() { return "exec-1"; }
        @Override public String nodeName() { return "node-a"; }
        @Override public int attempt() { return 0; }
        @Override public Logger logger() { return LoggerFactory.getLogger("test"); }
        @Override public MemoryStore memory() { return MemoryStore.noop(); }
    };

    private final List<ExecutorService> executorsToShutDown = new ArrayList<>();

    private Executor trackingWrap(ExecutorService delegate, Context ctx) {
        executorsToShutDown.add(delegate);
        return ContextPropagatingExecutor.wrap(delegate, ctx);
    }

    @BeforeEach
    void clearMdc() {
        MDC.clear();
    }

    @AfterEach
    void resetMdc() {
        MDC.clear();
        executorsToShutDown.forEach(ExecutorService::shutdown);
        executorsToShutDown.clear();
    }

    @Test
    void propagatesMdcToRunnableOnDifferentThread() throws InterruptedException {
        MDC.put("requestId", "req-42");
        AtomicReference<String> captured = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        var executor = trackingWrap(Executors.newSingleThreadExecutor(), STUB_CONTEXT);
        executor.execute(() -> {
            captured.set(MDC.get("requestId"));
            latch.countDown();
        });

        latch.await();
        assertThat(captured.get()).isEqualTo("req-42");
    }

    @Test
    void restoresMdcAfterRunnableCompletes() throws InterruptedException {
        MDC.put("before", "original");
        CountDownLatch latch = new CountDownLatch(1);

        ExecutorService singleThread = Executors.newSingleThreadExecutor();
        MDC.put("ctx", "caller");
        var wrapped = ContextPropagatingExecutor.wrap(singleThread, STUB_CONTEXT);

        AtomicReference<String> afterCtx = new AtomicReference<>();

        // prime the thread with some MDC state, then check it's restored afterward
        wrapped.execute(() -> MDC.put("ctx", "inside"));

        // second task runs on the same thread; previous MDC from submitter should not bleed
        wrapped.execute(() -> {
            afterCtx.set(MDC.get("ctx"));
            latch.countDown();
        });

        latch.await();
        // the second task's MDC should reflect the snapshot at wrap-time (has "ctx"="caller")
        assertThat(afterCtx.get()).isEqualTo("caller");
        singleThread.shutdown();
    }

    @Test
    void handlesNullMdcGracefully() throws InterruptedException {
        // MDC is cleared — getCopyOfContextMap() may return null
        MDC.clear();
        AtomicBoolean ran = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        var executor = trackingWrap(Executors.newSingleThreadExecutor(), STUB_CONTEXT);
        executor.execute(() -> {
            ran.set(true);
            latch.countDown();
        });

        latch.await();
        assertThat(ran.get()).isTrue();
    }

    @Test
    void executorServiceVariantDelegatesLifecycleMethods() {
        ExecutorService mockDelegate = Executors.newSingleThreadExecutor();
        ExecutorService wrapped = ContextPropagatingExecutor.wrap(mockDelegate, STUB_CONTEXT);

        assertThat(wrapped.isShutdown()).isFalse();
        assertThat(wrapped.isTerminated()).isFalse();

        wrapped.shutdown();

        assertThat(wrapped.isShutdown()).isTrue();
        assertThat(mockDelegate.isShutdown()).isTrue();
    }

    @Test
    void executorServiceVariantPropagatesMdc() throws InterruptedException, ExecutionException {
        MDC.put("traceId", "trace-99");
        AtomicReference<String> captured = new AtomicReference<>();

        ExecutorService wrapped = ContextPropagatingExecutor.wrap(
                Executors.newSingleThreadExecutor(), STUB_CONTEXT);

        var future = wrapped.submit(() -> captured.set(MDC.get("traceId")));
        future.get();

        assertThat(captured.get()).isEqualTo("trace-99");
        wrapped.shutdown();
    }

    @Test
    void injectsExecutionIdIntoMdc() throws InterruptedException {
        AtomicReference<String> captured = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        var executor = trackingWrap(Executors.newSingleThreadExecutor(), STUB_CONTEXT);
        executor.execute(() -> {
            captured.set(MDC.get("tracegraph.executionId"));
            latch.countDown();
        });

        latch.await();
        assertThat(captured.get()).isEqualTo("exec-1");
    }

    @Test
    void invokeAllPropagatesMdc() throws InterruptedException, ExecutionException {
        MDC.put("key", "value");
        ExecutorService wrapped = ContextPropagatingExecutor.wrap(
                Executors.newFixedThreadPool(2), STUB_CONTEXT);

        var futures = wrapped.invokeAll(List.of(
                () -> MDC.get("key"),
                () -> MDC.get("key")
        ));

        for (var f : futures) {
            assertThat(f.get()).isEqualTo("value");
        }
        wrapped.shutdown();
    }
}
