package io.tracegraph.memory.window;

import io.tracegraph.core.spi.MemoryStore;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * {@link MemoryStore} decorator that expires entries after a configurable TTL.
 *
 * <p>Expiry is lazy — entries are only removed when accessed or when {@link #evictExpired()} is
 * called explicitly. No background thread is used to keep the implementation Loom-safe.
 *
 * <pre>{@code
 * MemoryStore ttl = TtlMemoryStore.wrap(new InMemoryMemoryStore())
 *     .ttl(Duration.ofMinutes(30))
 *     .build();
 * }</pre>
 */
public final class TtlMemoryStore implements MemoryStore {

    private record Entry(Object value, long expiresAt) {
        boolean expired(long now) { return expiresAt <= now; }
    }

    private final MemoryStore delegate;
    private final long ttlMillis;
    private final Clock clock;
    private final ConcurrentMap<String, ConcurrentMap<String, Entry>> ttlMap = new ConcurrentHashMap<>();

    private TtlMemoryStore(Builder b) {
        this.delegate = b.delegate;
        this.ttlMillis = b.ttl.toMillis();
        this.clock = b.clock;
    }

    public static Builder wrap(MemoryStore delegate) {
        return new Builder(delegate);
    }

    @Override
    public Optional<Object> get(String scope, String key) {
        Entry entry = ttlEntry(scope, key);
        if (entry != null && entry.expired(clock.millis())) {
            evict(scope, key);
            return Optional.empty();
        }
        return entry != null ? Optional.of(entry.value()) : Optional.empty();
    }

    @Override
    public void put(String scope, String key, Object value) {
        long exp = clock.millis() + ttlMillis;
        ttlMap.computeIfAbsent(scope, s -> new ConcurrentHashMap<>())
              .put(key, new Entry(value, exp));
    }

    @Override
    public boolean delete(String scope, String key) {
        ConcurrentMap<String, Entry> entries = ttlMap.get(scope);
        if (entries != null) entries.remove(key);
        return true;
    }

    @Override
    public Set<String> keys(String scope) {
        ConcurrentMap<String, Entry> entries = ttlMap.get(scope);
        if (entries == null) return Set.of();
        long now = clock.millis();
        return entries.entrySet().stream()
                .filter(e -> !e.getValue().expired(now))
                .map(java.util.Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Eagerly remove all expired entries across all scopes. */
    public void evictExpired() {
        long now = clock.millis();
        ttlMap.forEach((scope, entries) -> entries.entrySet()
                .removeIf(e -> e.getValue().expired(now)));
    }

    private Entry ttlEntry(String scope, String key) {
        ConcurrentMap<String, Entry> entries = ttlMap.get(scope);
        return entries == null ? null : entries.get(key);
    }

    private void evict(String scope, String key) {
        ConcurrentMap<String, Entry> entries = ttlMap.get(scope);
        if (entries != null) entries.remove(key);
    }

    public static final class Builder {
        private final MemoryStore delegate;
        private Duration ttl = Duration.ofHours(1);
        private Clock clock = Clock.systemUTC();

        private Builder(MemoryStore delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
        }

        public Builder ttl(Duration ttl) {
            Objects.requireNonNull(ttl, "ttl");
            if (ttl.isNegative() || ttl.isZero()) throw new IllegalArgumentException("TTL must be positive");
            this.ttl = ttl;
            return this;
        }

        Builder clock(Clock clock) {
            this.clock = Objects.requireNonNull(clock, "clock");
            return this;
        }

        public TtlMemoryStore build() {
            return new TtlMemoryStore(this);
        }
    }
}
