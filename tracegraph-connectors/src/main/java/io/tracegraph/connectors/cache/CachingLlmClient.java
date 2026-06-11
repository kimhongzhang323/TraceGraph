package io.tracegraph.connectors.cache;

import io.tracegraph.connectors.llm.LlmClient;
import io.tracegraph.connectors.llm.LlmRequest;
import io.tracegraph.connectors.llm.LlmResponse;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * {@link LlmClient} decorator that caches responses by exact {@link LlmRequest} equality — the
 * same matching semantics as {@code RecordedLlmClient} replay.
 *
 * <p>Identical requests (messages, model, temperature, maxTokens, tools) are served from the
 * cache without touching the delegate; the complement to {@link SemanticLlmCache}, which matches
 * by embedding similarity. Failures are never cached — an exception propagates and the next
 * identical request hits the delegate again.
 *
 * <p>Eviction is least-recently-used with capacity {@code maxSize} (default 1024). The delegate
 * call happens outside the cache lock, so concurrent misses may both reach the delegate
 * (last-completed wins the cache slot) — deliberate: blocking unrelated requests behind a slow
 * LLM call would serialize the graph.
 */
public final class CachingLlmClient implements LlmClient {

    private static final int DEFAULT_MAX_SIZE = 1024;

    private final LlmClient delegate;
    private final ReentrantLock lock = new ReentrantLock();
    private final LinkedHashMap<LlmRequest, LlmResponse> cache;

    private CachingLlmClient(LlmClient delegate, int maxSize) {
        this.delegate = delegate;
        this.cache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<LlmRequest, LlmResponse> eldest) {
                return size() > maxSize;
            }
        };
    }

    /** Creates a cache with the default capacity of {@value #DEFAULT_MAX_SIZE} entries. */
    public static CachingLlmClient of(LlmClient delegate) {
        return of(delegate, DEFAULT_MAX_SIZE);
    }

    /** Creates a cache evicting least-recently-used entries beyond {@code maxSize}. */
    public static CachingLlmClient of(LlmClient delegate, int maxSize) {
        Objects.requireNonNull(delegate, "delegate");
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be > 0");
        }
        return new CachingLlmClient(delegate, maxSize);
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        lock.lock();
        try {
            LlmResponse cached = cache.get(request);
            if (cached != null) {
                return cached;
            }
        } finally {
            lock.unlock();
        }
        LlmResponse response = delegate.complete(request);
        lock.lock();
        try {
            cache.put(request, response);
        } finally {
            lock.unlock();
        }
        return response;
    }

    @Override
    public String systemName() {
        return delegate.systemName();
    }

    /** Number of cached responses. */
    public int size() {
        lock.lock();
        try {
            return cache.size();
        } finally {
            lock.unlock();
        }
    }
}
