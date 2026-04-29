package io.tracegraph.connectors.llm;

import java.util.concurrent.Flow;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.SubmissionPublisher;

/**
 * SPI for invoking LLM completions.
 * <p>
 * Implementations must be thread-safe. The {@link #stream(LlmRequest)} method has a
 * default implementation that wraps {@link #complete(LlmRequest)} into a single-chunk
 * publisher; providers with native streaming support should override it.
 */
@FunctionalInterface
public interface LlmClient {

    /** Blocking completion call. */
    LlmResponse complete(LlmRequest request);

    /**
     * Streaming completion call.
     * <p>
     * Returns a {@link Flow.Publisher} that emits {@link LlmStreamChunk}s as tokens arrive.
     * The default implementation wraps {@link #complete(LlmRequest)} into a single-chunk
     * publisher and is suitable for testing; production implementations should override.
     *
     * @param request the LLM request
     * @return a publisher of streaming chunks
     */
    default Flow.Publisher<LlmStreamChunk> stream(LlmRequest request) {
        // Use Executors.newSingleThreadExecutor as the publisher's executor
        // to ensure submit() and close() are serialized with onNext/onComplete
        SubmissionPublisher<LlmStreamChunk> pub = new SubmissionPublisher<>(
                ForkJoinPool.commonPool(), Flow.defaultBufferSize());
        Thread.startVirtualThread(() -> {
            try {
                LlmResponse full = complete(request);
                // submit() guarantees delivery to all subscribers
                pub.submit(LlmStreamChunk.done(full.content(), full.finish()));
                // Allow time for subscriber onNext processing before close triggers onComplete
                Thread.sleep(10);
                pub.close();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                pub.closeExceptionally(e);
            } catch (Throwable t) {
                pub.closeExceptionally(t);
            }
        });
        return pub;
    }
}
