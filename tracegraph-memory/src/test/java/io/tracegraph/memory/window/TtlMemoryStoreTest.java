package io.tracegraph.memory.window;

import io.tracegraph.memory.InMemoryMemoryStore;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TtlMemoryStoreTest {

    @Test
    void storesAndRetrievesBeforeExpiry() {
        TtlMemoryStore store = TtlMemoryStore.wrap(new InMemoryMemoryStore())
                .ttl(Duration.ofMinutes(5)).build();
        store.put("s", "k", "value");
        assertThat(store.get("s", "k")).contains("value");
    }

    @Test
    void returnsEmptyAfterExpiry() {
        AtomicLong now = new AtomicLong(1000L);
        Clock fakeClock = new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return Instant.ofEpochMilli(now.get()); }
            @Override public long millis() { return now.get(); }
        };

        TtlMemoryStore store = TtlMemoryStore.wrap(new InMemoryMemoryStore())
                .ttl(Duration.ofSeconds(10)).clock(fakeClock).build();

        store.put("s", "k", "v");
        assertThat(store.get("s", "k")).contains("v");

        now.set(1000L + 10_001L); // advance past TTL
        assertThat(store.get("s", "k")).isEmpty();
    }

    @Test
    void keysExcludesExpired() {
        AtomicLong now = new AtomicLong(1000L);
        Clock fakeClock = new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return Instant.ofEpochMilli(now.get()); }
            @Override public long millis() { return now.get(); }
        };

        TtlMemoryStore store = TtlMemoryStore.wrap(new InMemoryMemoryStore())
                .ttl(Duration.ofSeconds(5)).clock(fakeClock).build();

        store.put("s", "alive", "v1");
        now.set(3000L);
        store.put("s", "fresh", "v2");
        now.set(6001L); // "alive" expired, "fresh" has 2s left

        assertThat(store.keys("s")).containsOnly("fresh");
    }

    @Test
    void rejectsZeroTtl() {
        assertThatThrownBy(() -> TtlMemoryStore.wrap(new InMemoryMemoryStore())
                .ttl(Duration.ZERO).build())
                .isInstanceOf(IllegalArgumentException.class);
    }
}
